package com.example.agent.runtime

import com.example.agent.llm.LlmApiException
import com.example.agent.llm.LlmClient
import com.example.agent.llm.LlmStreamException
import com.example.agent.model.LlmMessage
import com.example.agent.model.LlmRequest
import com.example.agent.model.StreamEvent
import com.example.agent.model.ToolCall
import com.example.agent.permissions.Permission
import com.example.agent.permissions.PermissionPolicy
import com.example.agent.tools.StoragePermissionException
import com.example.agent.tools.ToolException
import com.example.agent.tools.ToolRegistry
import com.example.agent.tools.ToolResult
import com.example.agent.tools.ToolStatus
import com.example.agent.tools.truncateOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Máquina de estados del agente:
 *
 *  IDLE → STREAMING → (sin tool_calls) → COMPLETED
 *                   → (con tool_calls) → [AWAITING_APPROVAL] → RUNNING_TOOL → STREAMING → ...
 *  Terminales: COMPLETED, FAILED, STEP_LIMIT. CANCELLED lo fija quien cancela la corrutina.
 */
enum class AgentPhase { IDLE, STREAMING, AWAITING_APPROVAL, RUNNING_TOOL, COMPLETED, FAILED, CANCELLED, STEP_LIMIT }

enum class ApprovalDecision { ALLOW_ONCE, ALLOW_SESSION, DENY }

class ApprovalRequest(val call: ToolCall, val summary: String, val canRemember: Boolean)

/** Quien muestra la petición al usuario (la UI) y suspende hasta que decide. */
fun interface ApprovalGate {
    suspend fun request(request: ApprovalRequest): ApprovalDecision
}

sealed interface AgentEvent {
    data class Phase(val phase: AgentPhase, val step: Int) : AgentEvent
    data class Text(val delta: String) : AgentEvent
    data class Reasoning(val delta: String) : AgentEvent
    data class ToolCallPreview(val id: String, val name: String) : AgentEvent

    /** Un turno del modelo terminó (texto completo + tool_calls). Debe persistirse. */
    data class AssistantTurn(val message: LlmMessage.Assistant) : AgentEvent
    data class AwaitingApproval(val call: ToolCall, val summary: String, val canRemember: Boolean) : AgentEvent
    data class ToolStarted(val call: ToolCall, val summary: String) : AgentEvent
    data class ToolFinished(val call: ToolCall, val result: ToolResult) : AgentEvent
    data class Notice(val text: String) : AgentEvent

    /** Último evento de una ejecución que no fue cancelada. */
    data class Done(val phase: AgentPhase, val message: String? = null) : AgentEvent
}

class AgentRunner(
    private val llm: LlmClient,
    private val tools: ToolRegistry,
    private val policy: PermissionPolicy,
    private val approvals: ApprovalGate,
    private val model: String,
    private val maxSteps: Int = DEFAULT_MAX_STEPS
) {
    /**
     * Ejecuta el bucle del agente sobre [history] (incluye el prompt de sistema y el último mensaje
     * del usuario). Cancelar la colección cancela la petición HTTP, la espera de aprobación y la
     * herramienta en curso (incluido el proceso de shell).
     */
    fun run(history: List<LlmMessage>, toolsEnabled: Boolean = true): Flow<AgentEvent> = flow {
        val messages = history.toMutableList()
        var useTools = toolsEnabled
        var step = 0

        while (true) {
            if (step >= maxSteps) {
                emit(AgentEvent.Done(AgentPhase.STEP_LIMIT, "Alcancé el límite de $maxSteps pasos. Dime si quieres que continúe."))
                return@flow
            }
            step++
            emit(AgentEvent.Phase(AgentPhase.STREAMING, step))

            val text = StringBuilder()
            val reasoning = StringBuilder()
            var calls: List<ToolCall> = emptyList()
            var failure: Throwable? = null

            val request = LlmRequest(model, messages.toList(), if (useTools) tools.definitions() else emptyList())
            // `catch` solo captura errores del proveedor (upstream), nunca los del colector (UI)
            llm.stream(request)
                .catch { failure = it }
                .collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> { text.append(event.text); emit(AgentEvent.Text(event.text)) }
                        is StreamEvent.ReasoningDelta -> { reasoning.append(event.text); emit(AgentEvent.Reasoning(event.text)) }
                        is StreamEvent.ToolCallStarted -> emit(AgentEvent.ToolCallPreview(event.id, event.name))
                        is StreamEvent.Completed -> calls = event.toolCalls
                    }
                }

            val error = failure
            if (error != null) {
                if (useTools && error is LlmApiException && error.suggestsToolsUnsupported) {
                    // Muchos modelos gratuitos no admiten tools: se reintenta el turno solo como chat
                    useTools = false
                    step--
                    emit(AgentEvent.Notice("Este modelo no admite herramientas; continúo solo como chat."))
                    continue
                }
                emit(AgentEvent.Done(AgentPhase.FAILED, describe(error)))
                return@flow
            }

            if (text.isBlank() && calls.isEmpty()) {
                emit(AgentEvent.Done(AgentPhase.FAILED, "El modelo devolvió una respuesta vacía."))
                return@flow
            }

            val assistant = LlmMessage.Assistant(text.toString(), calls, reasoning.toString())
            messages += assistant
            emit(AgentEvent.AssistantTurn(assistant))

            if (calls.isEmpty()) {
                emit(AgentEvent.Done(AgentPhase.COMPLETED))
                return@flow
            }

            for (call in calls) {
                val result = runToolCall(call, step)
                messages += LlmMessage.Tool(call.id, call.name, truncateOutput(result.output))
            }
        }
    }

    private suspend fun FlowCollector<AgentEvent>.runToolCall(call: ToolCall, step: Int): ToolResult {
        val tool = tools.get(call.name)
            ?: return finish(call, ToolResult.error("Herramienta desconocida: ${call.name}. Disponibles: ${tools.all.joinToString { it.name }}"))

        val args = try {
            JSONObject(call.argumentsJson.ifBlank { "{}" })
        } catch (e: JSONException) {
            return finish(call, ToolResult.error("Los argumentos no son un JSON válido: ${call.argumentsJson.take(200)}"))
        }

        // Se valida ANTES de pedir permiso: no se molesta al usuario con llamadas que van a fallar
        try {
            tool.validate(args)
        } catch (e: ToolException) {
            return finish(call, ToolResult.error(e.message ?: "Argumentos inválidos", e is StoragePermissionException))
        }

        val summary = tool.summarize(args)
        when (policy.decide(call.name)) {
            Permission.DENY -> return finish(call, ToolResult.denied("Herramienta bloqueada por el usuario en Ajustes → Permisos de herramientas. No la vuelvas a intentar; dile al usuario que puede activarla ahí."))
            Permission.ASK -> {
                val canRemember = policy.canRemember(call.name)
                emit(AgentEvent.Phase(AgentPhase.AWAITING_APPROVAL, step))
                emit(AgentEvent.AwaitingApproval(call, summary, canRemember))
                when (approvals.request(ApprovalRequest(call, summary, canRemember))) {
                    ApprovalDecision.DENY -> return finish(call, ToolResult.denied("El usuario rechazó esta acción. Pregúntale qué prefiere o propón otra alternativa."))
                    ApprovalDecision.ALLOW_SESSION -> policy.grantForSession(call.name)
                    ApprovalDecision.ALLOW_ONCE -> Unit
                }
            }
            Permission.AUTO -> Unit
        }

        emit(AgentEvent.Phase(AgentPhase.RUNNING_TOOL, step))
        emit(AgentEvent.ToolStarted(call, summary))
        val result = try {
            tool.execute(args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolException) {
            ToolResult.error(e.message ?: "Error en la herramienta", e is StoragePermissionException)
        } catch (e: Exception) {
            ToolResult.error("Error inesperado en ${call.name}: ${e.message ?: e.javaClass.simpleName}")
        }
        return finish(call, result)
    }

    private suspend fun FlowCollector<AgentEvent>.finish(call: ToolCall, result: ToolResult): ToolResult {
        emit(AgentEvent.ToolFinished(call, result))
        return result
    }

    private fun describe(error: Throwable): String = when (error) {
        is LlmApiException -> when (error.code) {
            401, 403 -> "El proveedor rechazó la API key (HTTP ${error.code}). Revísala en Ajustes."
            429 -> "Límite de uso alcanzado (HTTP 429). Espera un momento o cambia de modelo."
            else -> "El proveedor respondió con error: ${error.message}"
        }
        is LlmStreamException -> "Error del proveedor: ${error.message}"
        is IOException -> "Error de red: ${error.message ?: "sin conexión"}"
        else -> error.message ?: "Error desconocido"
    }

    companion object {
        const val DEFAULT_MAX_STEPS = 20
    }
}
