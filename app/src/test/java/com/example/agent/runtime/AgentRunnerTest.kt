package com.example.agent.runtime

import com.example.agent.llm.LlmApiException
import com.example.agent.llm.LlmClient
import com.example.agent.model.LlmMessage
import com.example.agent.model.LlmRequest
import com.example.agent.model.StreamEvent
import com.example.agent.model.ToolCall
import com.example.agent.permissions.Permission
import com.example.agent.permissions.PermissionPolicy
import com.example.agent.tools.EditFileTool
import com.example.agent.tools.GrepTool
import com.example.agent.tools.ListDirTool
import com.example.agent.tools.ReadFileTool
import com.example.agent.tools.ToolRegistry
import com.example.agent.tools.ToolStatus
import com.example.agent.tools.Workspace
import com.example.agent.tools.WriteFileTool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** LLM falso: cada elemento de [turns] es la respuesta (en streaming) de una llamada sucesiva. */
private class ScriptedLlm(private val turns: List<suspend FlowCollector<StreamEvent>.() -> Unit>) : LlmClient {
    val requests = mutableListOf<LlmRequest>()
    override fun stream(request: LlmRequest): Flow<StreamEvent> = flow {
        requests += request
        val turn = turns.getOrNull(requests.size - 1) ?: error("El agente hizo más llamadas al modelo de las previstas")
        turn()
    }
}

private fun toolTurn(vararg calls: ToolCall): suspend FlowCollector<StreamEvent>.() -> Unit = {
    calls.forEach { emit(StreamEvent.ToolCallStarted(0, it.id, it.name)) }
    emit(StreamEvent.Completed("tool_calls", calls.toList()))
}

private fun textTurn(text: String): suspend FlowCollector<StreamEvent>.() -> Unit = {
    text.chunked(5).forEach { emit(StreamEvent.TextDelta(it)) }
    emit(StreamEvent.Completed("stop", emptyList()))
}

@OptIn(ExperimentalCoroutinesApi::class)
class AgentRunnerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var ws: Workspace
    private lateinit var registry: ToolRegistry
    private lateinit var policy: PermissionPolicy

    private val approvedSummaries = mutableListOf<String>()
    private var nextDecision = ApprovalDecision.ALLOW_ONCE
    private val gate = ApprovalGate { req -> approvedSummaries += req.summary; nextDecision }

    @Before fun setUp() {
        ws = Workspace(File(tmp.root, "ws"))
        registry = ToolRegistry(listOf(ReadFileTool(ws), WriteFileTool(ws), EditFileTool(ws), ListDirTool(ws), GrepTool(ws)))
        policy = PermissionPolicy()
    }

    private fun runner(llm: LlmClient, maxSteps: Int = 12, approvals: ApprovalGate = gate) =
        AgentRunner(llm, registry, policy, approvals, "modelo-test", maxSteps)

    private val history = listOf<LlmMessage>(LlmMessage.System("sys"), LlmMessage.User("haz algo"))

    // ---------------------------------------------------------------------------------------
    @Test fun createReadEdit_fullLoop_endToEnd() = runTest {
        val write = ToolCall("c1", "write_file", """{"path":"notas.txt","content":"hola mundo\nlinea 2\n"}""")
        val read = ToolCall("c2", "read_file", """{"path":"notas.txt"}""")
        val edit = ToolCall("c3", "edit_file", """{"path":"notas.txt","old_string":"hola mundo","new_string":"hola Helix"}""")
        val llm = ScriptedLlm(listOf(toolTurn(write), toolTurn(read), toolTurn(edit), textTurn("Listo, archivo creado y editado.")))

        val events = runner(llm).run(history).toList()

        // 1) el archivo quedó como el modelo lo pidió
        assertEquals("hola Helix\nlinea 2\n", File(ws.root, "notas.txt").readText())

        // 2) las tres herramientas terminaron bien, en orden
        val finished = events.filterIsInstance<AgentEvent.ToolFinished>()
        assertEquals(listOf("write_file", "read_file", "edit_file"), finished.map { it.call.name })
        assertTrue(finished.all { it.result.status == ToolStatus.OK })

        // 3) solo escribir y editar pidieron aprobación; leer es automático
        assertEquals(listOf("Escribir notas.txt", "Editar notas.txt"), approvedSummaries)

        // 4) el modelo recibió el resultado de read_file con el contenido real antes de editar
        val toolMsg = llm.requests[2].messages.last() as LlmMessage.Tool
        assertEquals("c2", toolMsg.toolCallId)
        assertTrue(toolMsg.content.contains("hola mundo"))

        // 5) cada petición incluyó TODO el historial previo (memoria dentro del bucle)
        assertEquals(listOf(2, 4, 6, 8), llm.requests.map { it.messages.size })
        assertTrue(llm.requests.all { it.tools.isNotEmpty() })

        // 6) máquina de estados y cierre
        val phases = events.filterIsInstance<AgentEvent.Phase>().map { it.phase }
        assertTrue(phases.containsAll(listOf(AgentPhase.STREAMING, AgentPhase.AWAITING_APPROVAL, AgentPhase.RUNNING_TOOL)))
        assertEquals(AgentEvent.Done(AgentPhase.COMPLETED), events.last())
        assertEquals(4, events.filterIsInstance<AgentEvent.AssistantTurn>().size)
        assertEquals("Listo, archivo creado y editado.", events.filterIsInstance<AgentEvent.Text>().joinToString("") { it.delta })
    }

    @Test fun plainTextAnswer_completesInOneStep() = runTest {
        val events = runner(ScriptedLlm(listOf(textTurn("Hola")))).run(history).toList()
        assertEquals(AgentEvent.Done(AgentPhase.COMPLETED), events.last())
        assertTrue(events.none { it is AgentEvent.AwaitingApproval })
    }

    @Test fun toolsDisabled_sendsNoToolDefinitions() = runTest {
        val llm = ScriptedLlm(listOf(textTurn("ok")))
        runner(llm).run(history, toolsEnabled = false).toList()
        assertTrue(llm.requests.single().tools.isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    @Test fun deniedByUser_doesNotRunTheTool_andModelIsTold() = runTest {
        nextDecision = ApprovalDecision.DENY
        val write = ToolCall("c1", "write_file", """{"path":"x.txt","content":"no"}""")
        val llm = ScriptedLlm(listOf(toolTurn(write), textTurn("Entendido")))

        val events = runner(llm).run(history).toList()

        assertFalse(File(ws.root, "x.txt").exists())
        val finished = events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertEquals(ToolStatus.DENIED, finished.result.status)
        assertTrue((llm.requests[1].messages.last() as LlmMessage.Tool).content.contains("rechazó"))
        assertEquals(AgentEvent.Done(AgentPhase.COMPLETED), events.last())
    }

    @Test fun allowForSession_asksOnlyOnce() = runTest {
        nextDecision = ApprovalDecision.ALLOW_SESSION
        val w1 = ToolCall("c1", "write_file", """{"path":"a.txt","content":"1"}""")
        val w2 = ToolCall("c2", "write_file", """{"path":"b.txt","content":"2"}""")
        val llm = ScriptedLlm(listOf(toolTurn(w1), toolTurn(w2), textTurn("ok")))

        runner(llm).run(history).toList()

        assertEquals(1, approvedSummaries.size)
        assertTrue(File(ws.root, "a.txt").exists() && File(ws.root, "b.txt").exists())
    }

    @Test fun toolBlockedInSettings_isNeverExecutedNorAsked() = runTest {
        policy.setLevel("write_file", Permission.DENY)
        val llm = ScriptedLlm(listOf(toolTurn(ToolCall("c1", "write_file", """{"path":"a.txt","content":"1"}""")), textTurn("ok")))

        val events = runner(llm).run(history).toList()

        assertTrue(approvedSummaries.isEmpty())
        assertFalse(File(ws.root, "a.txt").exists())
        assertEquals(ToolStatus.DENIED, events.filterIsInstance<AgentEvent.ToolFinished>().single().result.status)
    }

    // ---------------------------------------------------------------------------------------
    @Test fun pathTraversal_isRejectedBeforeAskingTheUser() = runTest {
        File(tmp.root, "secreto.txt").writeText("no")
        val llm = ScriptedLlm(
            listOf(
                toolTurn(ToolCall("c1", "read_file", """{"path":"../secreto.txt"}""")),
                toolTurn(ToolCall("c2", "write_file", """{"path":"../../escape.txt","content":"x"}""")),
                textTurn("ok")
            )
        )

        val events = runner(llm).run(history).toList()

        val results = events.filterIsInstance<AgentEvent.ToolFinished>()
        assertTrue(results.all { it.result.status == ToolStatus.ERROR && it.result.output.contains("..") })
        assertTrue("no debe molestar al usuario con llamadas inválidas", approvedSummaries.isEmpty())
        assertFalse(File(tmp.root, "escape.txt").exists())
    }

    @Test fun unknownTool_andInvalidJson_areReturnedAsErrorsToTheModel() = runTest {
        val llm = ScriptedLlm(
            listOf(
                toolTurn(ToolCall("c1", "borrar_todo", "{}"), ToolCall("c2", "read_file", "{no es json")),
                textTurn("ok")
            )
        )
        val events = runner(llm).run(history).toList()
        val results = events.filterIsInstance<AgentEvent.ToolFinished>()
        assertTrue(results[0].result.output.contains("desconocida"))
        assertTrue(results[1].result.output.contains("JSON"))
        // ambos resultados se devolvieron al modelo (la API exige uno por cada tool_call)
        assertEquals(2, llm.requests[1].messages.filterIsInstance<LlmMessage.Tool>().size)
    }

    // ---------------------------------------------------------------------------------------
    @Test fun stepLimit_stopsRunawayLoops() = runTest {
        val read = ToolCall("c", "list_dir", "{}")
        val llm = ScriptedLlm(List(10) { toolTurn(read.copy(id = "c$it")) })

        val events = runner(llm, maxSteps = 3).run(history).toList()

        assertEquals(3, llm.requests.size)
        val done = events.last() as AgentEvent.Done
        assertEquals(AgentPhase.STEP_LIMIT, done.phase)
        assertTrue(done.message!!.contains("3"))
    }

    // ---------------------------------------------------------------------------------------
    @Test fun modelWithoutToolSupport_fallsBackToPlainChat() = runTest {
        val llm = ScriptedLlm(
            listOf(
                { throw LlmApiException(404, "No endpoints found that support tool use") },
                textTurn("Respuesta sin herramientas")
            )
        )
        val events = runner(llm).run(history).toList()

        assertTrue(events.any { it is AgentEvent.Notice })
        assertTrue(llm.requests[0].tools.isNotEmpty())
        assertTrue(llm.requests[1].tools.isEmpty())
        assertEquals(AgentEvent.Done(AgentPhase.COMPLETED), events.last())
    }

    @Test fun providerErrors_endInFailedWithAUserFacingMessage() = runTest {
        val unauthorized = runner(ScriptedLlm(listOf({ throw LlmApiException(401, "bad key") }))).run(history).toList().last() as AgentEvent.Done
        assertEquals(AgentPhase.FAILED, unauthorized.phase)
        assertTrue(unauthorized.message!!.contains("API key"))

        val rateLimited = runner(ScriptedLlm(listOf({ throw LlmApiException(429, "slow down") }))).run(history).toList().last() as AgentEvent.Done
        assertTrue(rateLimited.message!!.contains("429"))

        val network = runner(ScriptedLlm(listOf({ throw IOException("timeout") }))).run(history).toList().last() as AgentEvent.Done
        assertTrue(network.message!!.contains("red"))
    }

    @Test fun emptyModelResponse_isReportedAsFailure() = runTest {
        val done = runner(ScriptedLlm(listOf({ emit(StreamEvent.Completed("stop", emptyList())) }))).run(history).toList().last() as AgentEvent.Done
        assertEquals(AgentPhase.FAILED, done.phase)
    }

    // ---------------------------------------------------------------------------------------
    @Test fun cancelWhileWaitingForApproval_neverRunsTheTool() = runTest {
        val never = CompletableDeferred<ApprovalDecision>()
        val waitingGate = ApprovalGate { never.await() }
        val llm = ScriptedLlm(listOf(toolTurn(ToolCall("c1", "write_file", """{"path":"a.txt","content":"x"}"""))))
        val events = mutableListOf<AgentEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            runner(llm, approvals = waitingGate).run(history).collect { events += it }
        }
        assertTrue("debía quedarse esperando la aprobación", events.any { it is AgentEvent.AwaitingApproval })

        job.cancel()
        job.join()

        assertFalse(File(ws.root, "a.txt").exists())
        assertTrue(events.none { it is AgentEvent.ToolFinished || it is AgentEvent.Done })
    }

    @Test fun uiFailureWhileCollecting_isNotMistakenForAProviderError() = runTest {
        val llm = ScriptedLlm(listOf(textTurn("hola mundo")))
        try {
            runner(llm).run(history).collect { if (it is AgentEvent.Text) throw IllegalStateException("fallo de UI") }
            throw AssertionError("la excepción del colector debía propagarse")
        } catch (e: IllegalStateException) {
            assertEquals("fallo de UI", e.message)
        }
    }
}
