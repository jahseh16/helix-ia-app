package com.example.agent.model

import org.json.JSONObject

/** Llamada a herramienta pedida por el modelo. [argumentsJson] es el JSON crudo de argumentos. */
data class ToolCall(val id: String, val name: String, val argumentsJson: String)

/** Mensaje de la conversación en el formato que consume el LLM (independiente de la base de datos). */
sealed interface LlmMessage {
    data class System(val content: String) : LlmMessage
    data class User(val content: String) : LlmMessage
    data class Assistant(
        val content: String,
        val toolCalls: List<ToolCall> = emptyList(),
        val reasoning: String = ""
    ) : LlmMessage

    data class Tool(val toolCallId: String, val name: String, val content: String) : LlmMessage
}

/** Definición de herramienta tal como se anuncia al modelo (JSON Schema en [parameters]). */
class ToolDefinition(val name: String, val description: String, val parameters: JSONObject)

class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val tools: List<ToolDefinition> = emptyList()
)

/** Eventos normalizados de una respuesta en streaming. */
sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ReasoningDelta(val text: String) : StreamEvent

    /** El modelo empezó a declarar una llamada a herramienta (ya se conoce su nombre). */
    data class ToolCallStarted(val index: Int, val id: String, val name: String) : StreamEvent

    /** Fin del turno. [toolCalls] trae las llamadas ya completas, con argumentos ensamblados. */
    data class Completed(val finishReason: String?, val toolCalls: List<ToolCall>) : StreamEvent
}
