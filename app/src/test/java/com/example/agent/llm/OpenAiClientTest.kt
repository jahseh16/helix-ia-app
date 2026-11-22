package com.example.agent.llm

import com.example.agent.model.LlmMessage
import com.example.agent.model.LlmRequest
import com.example.agent.model.StreamEvent
import com.example.agent.model.ToolCall
import com.example.agent.model.ToolDefinition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiClientTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client(key: String = "sk-test") =
        OpenAiCompatClient(OkHttpClient(), server.url("/v1/chat/completions").toString(), key)

    private val tool = ToolDefinition("read_file", "Lee un archivo", JSONObject("""{"type":"object","properties":{"path":{"type":"string"}}}"""))

    @Test
    fun requestBody_carriesFullHistory_toolsAndStreamFlag() {
        server.enqueue(MockResponse().setBody("data: [DONE]\n\n"))
        val request = LlmRequest(
            model = "m1",
            messages = listOf(
                LlmMessage.System("sys"),
                LlmMessage.User("crea a.txt"),
                LlmMessage.Assistant("", listOf(ToolCall("c1", "write_file", """{"path":"a.txt"}"""))),
                LlmMessage.Tool("c1", "write_file", "Creado a.txt"),
                LlmMessage.User("ahora léelo")
            ),
            tools = listOf(tool)
        )
        runBlocking { client().stream(request).toList() }

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("m1", body.getString("model"))
        assertTrue(body.getBoolean("stream"))
        assertEquals("auto", body.getString("tool_choice"))
        assertEquals("read_file", body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))

        val msgs = body.getJSONArray("messages")
        assertEquals(5, msgs.length()) // historial completo, no solo el último mensaje
        assertEquals(listOf("system", "user", "assistant", "tool", "user"), (0 until 5).map { msgs.getJSONObject(it).getString("role") })
        val assistant = msgs.getJSONObject(2)
        assertTrue("content debe ser null si solo hay tool_calls", assistant.isNull("content"))
        assertEquals("c1", assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("c1", msgs.getJSONObject(3).getString("tool_call_id"))
    }

    @Test
    fun noToolsField_whenToolListIsEmpty() {
        server.enqueue(MockResponse().setBody("data: [DONE]\n\n"))
        runBlocking { client().stream(LlmRequest("m", listOf(LlmMessage.User("hola")))).toList() }
        val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        assertTrue(!body.has("tools") && !body.has("tool_choice"))
    }

    @Test
    fun streamsTextAndToolCallsFromRealHttpResponse() {
        val sse = buildString {
            append("""data: {"choices":[{"delta":{"content":"Voy"}}]}""" + "\n\n")
            append(": keep-alive\n\n")
            append("""data: {"choices":[{"delta":{"content":" a leer"}}]}""" + "\n\n")
            append("""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c9","function":{"name":"read_file","arguments":"{\"path\""}}]}}]}""" + "\n\n")
            append("""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":":\"a.txt\"}"}}]},"finish_reason":"tool_calls"}]}""" + "\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sse))

        val events = runBlocking { client().stream(LlmRequest("m", listOf(LlmMessage.User("x")), listOf(tool))).toList() }

        assertEquals(listOf("Voy", " a leer"), events.filterIsInstance<StreamEvent.TextDelta>().map { it.text })
        val done = events.last() as StreamEvent.Completed
        assertEquals(ToolCall("c9", "read_file", """{"path":"a.txt"}"""), done.toolCalls.single())
    }

    @Test
    fun httpError_isMappedToApiExceptionWithProviderMessage() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid API key"}}"""))
        try {
            runBlocking { client().stream(LlmRequest("m", listOf(LlmMessage.User("x")))).toList() }
            fail("debía lanzar")
        } catch (e: LlmApiException) {
            assertEquals(401, e.code)
            assertEquals("Invalid API key", e.detail)
        }
    }

    @Test
    fun toolsUnsupportedError_isDetected() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"No endpoints found that support tool use"}}"""))
        try {
            runBlocking { client().stream(LlmRequest("m", listOf(LlmMessage.User("x")), listOf(tool))).toList() }
            fail("debía lanzar")
        } catch (e: LlmApiException) {
            assertTrue(e.suggestsToolsUnsupported)
        }
    }

    @Test
    fun blankApiKey_failsBeforeAnyNetworkCall() {
        try {
            runBlocking { client(key = " ").stream(LlmRequest("m", listOf(LlmMessage.User("x")))).toList() }
            fail("debía lanzar")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("API key"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun cancellingTheCollector_abortsAStillRunningResponse() {
        val sse = (1..200).joinToString("") { """data: {"choices":[{"delta":{"content":"palabra $it "}}]}""" + "\n\n" }
        server.enqueue(MockResponse().setBody(sse).throttleBody(64, 100, TimeUnit.MILLISECONDS))

        val start = System.nanoTime()
        val first = runBlocking { client().stream(LlmRequest("m", listOf(LlmMessage.User("x")))).first() }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(first is StreamEvent.TextDelta)
        // La respuesta completa tardaría ~40 s: si volvió rápido, la cancelación abortó la llamada
        assertTrue("tardó $elapsedMs ms", elapsedMs < 5_000)
    }
}
