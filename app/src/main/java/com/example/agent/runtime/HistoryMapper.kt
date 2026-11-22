package com.example.agent.runtime

import com.example.agent.model.LlmMessage
import com.example.agent.model.ToolCall
import com.example.data.database.ChatMessageEntity
import org.json.JSONArray
import org.json.JSONObject

/** Convierte las filas guardadas en Room en el historial que se envía al modelo. */
object HistoryMapper {

    const val CANCELLED_TOOL_RESULT = "[La ejecución de esta herramienta fue cancelada o no llegó a terminar]"
    private const val DEFAULT_MAX_CHARS = 60_000

    fun toLlmMessages(rows: List<ChatMessageEntity>, maxChars: Int = DEFAULT_MAX_CHARS): List<LlmMessage> {
        val results = rows.filter { it.sender == ChatMessageEntity.SENDER_TOOL && it.toolCallId != null }
            .associateBy { it.toolCallId!! }

        val messages = mutableListOf<LlmMessage>()
        for (row in rows) {
            when (row.sender) {
                ChatMessageEntity.SENDER_USER -> messages += LlmMessage.User(userContent(row))
                ChatMessageEntity.SENDER_AI -> {
                    val calls = parseToolCalls(row.toolCallsJson)
                    messages += LlmMessage.Assistant(row.text, calls)
                    // La API exige un mensaje `tool` por cada tool_call. Si una ejecución se canceló,
                    // se sintetiza el resultado para que la conversación siga siendo válida.
                    calls.forEach { call ->
                        messages += LlmMessage.Tool(call.id, call.name, results[call.id]?.text ?: CANCELLED_TOOL_RESULT)
                    }
                }
                // TOOL se emite junto a su assistant; SYSTEM y ERROR son solo de interfaz
            }
        }
        return limit(messages, maxChars)
    }

    /**
     * Texto que ve el modelo para un mensaje del usuario. Si lleva imagen, se añade la descripción del modelo de
     * visión (el modelo de chat es de texto): así el agente "ve" la imagen sin que viaje ni se guarde base64.
     */
    fun userContent(row: ChatMessageEntity): String {
        val hasImage = row.imagePath != null || row.imageUrl != null
        return when {
            !row.imageAnalysis.isNullOrBlank() ->
                row.text.trim().let { if (it.isEmpty()) "" else "$it\n\n" } +
                    "[Imagen adjunta · descripción del modelo de visión]\n${row.imageAnalysis}"
            hasImage -> row.text.trim().let { if (it.isEmpty()) "" else "$it\n\n" } +
                "[El usuario adjuntó una imagen que no se pudo analizar]"
            else -> row.text
        }
    }

    /** Recorta por el principio, siempre empezando en un mensaje del usuario (no deja `tool` huérfanos). */
    fun limit(messages: List<LlmMessage>, maxChars: Int): List<LlmMessage> {
        var total = 0
        var cut = messages.size
        while (cut > 0) {
            val size = sizeOf(messages[cut - 1])
            if (total + size > maxChars && cut < messages.size) break
            total += size
            cut--
        }
        if (cut == 0) return messages
        val firstUser = (cut until messages.size).firstOrNull { messages[it] is LlmMessage.User }
            ?: messages.indexOfLast { it is LlmMessage.User }.takeIf { it >= 0 }
            ?: return messages
        return messages.subList(firstUser, messages.size)
    }

    private fun sizeOf(m: LlmMessage): Int = when (m) {
        is LlmMessage.System -> m.content.length
        is LlmMessage.User -> m.content.length
        is LlmMessage.Assistant -> m.content.length + m.toolCalls.sumOf { it.argumentsJson.length + it.name.length }
        is LlmMessage.Tool -> m.content.length
    }

    fun toolCallsToJson(calls: List<ToolCall>): String? =
        if (calls.isEmpty()) null else JSONArray().also { arr ->
            calls.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("arguments", it.argumentsJson)) }
        }.toString()

    fun parseToolCalls(json: String?): List<ToolCall> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ToolCall(o.getString("id"), o.getString("name"), o.optString("arguments", "{}"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
