package com.example.agent.llm

import com.example.agent.model.StreamEvent
import com.example.agent.model.ToolCall
import org.json.JSONException
import org.json.JSONObject
import java.util.TreeMap

/**
 * Parser incremental del stream SSE de `/chat/completions`. Sin dependencias de Android:
 * se alimenta línea a línea con [feed] y al llegar EOF se llama a [finish].
 *
 * Ensambla los `tool_calls` fragmentados (los argumentos llegan en trozos por índice) y los
 * emite una sola vez en [StreamEvent.Completed].
 */
class SseParser {

    private class CallAcc {
        var id = ""
        var name = ""
        val args = StringBuilder()
    }

    private val calls = TreeMap<Int, CallAcc>()
    private var lastIndex = 0
    private var finishReason: String? = null
    private var completed = false

    fun feed(rawLine: String): List<StreamEvent> {
        val line = rawLine.trimEnd('\r')
        if (line.isBlank() || line.startsWith(":")) return emptyList() // comentarios keep-alive
        if (!line.startsWith("data:")) return emptyList()

        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty()) return emptyList()
        if (payload == "[DONE]") return finish()

        val json = try {
            JSONObject(payload)
        } catch (e: JSONException) {
            return emptyList() // fragmento corrupto: se ignora, el stream sigue
        }

        json.optJSONObject("error")?.let {
            throw LlmStreamException(it.optString("message").ifBlank { "Error del proveedor" })
        }

        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return emptyList()
        val events = mutableListOf<StreamEvent>()
        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message")

        if (delta != null) {
            delta.stringOrNull("content")?.takeIf { it.isNotEmpty() }
                ?.let { events += StreamEvent.TextDelta(it) }

            (delta.stringOrNull("reasoning") ?: delta.stringOrNull("reasoning_content"))
                ?.takeIf { it.isNotEmpty() }
                ?.let { events += StreamEvent.ReasoningDelta(it) }

            delta.optJSONArray("tool_calls")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { applyToolCallChunk(it, events) }
                }
            }
        }

        choice.stringOrNull("finish_reason")?.let { finishReason = it }
        return events
    }

    /** Llamar al terminar el stream. Idempotente: solo emite [StreamEvent.Completed] una vez. */
    fun finish(): List<StreamEvent> {
        if (completed) return emptyList()
        completed = true
        val toolCalls = calls.entries
            .filter { it.value.name.isNotBlank() }
            .map { (index, acc) ->
                ToolCall(
                    id = acc.id.ifBlank { "call_$index" },
                    name = acc.name,
                    argumentsJson = acc.args.toString().ifBlank { "{}" }
                )
            }
        return listOf(StreamEvent.Completed(finishReason, toolCalls))
    }

    private fun applyToolCallChunk(chunk: JSONObject, events: MutableList<StreamEvent>) {
        val id = chunk.stringOrNull("id").orEmpty()
        var index = if (chunk.has("index")) chunk.optInt("index") else -1
        if (index < 0) {
            // Algunos proveedores omiten `index`: un `id` nuevo abre una llamada nueva
            index = if (id.isNotBlank() && calls[lastIndex]?.id?.isNotBlank() == true && calls[lastIndex]?.id != id) {
                lastIndex + 1
            } else {
                lastIndex
            }
        }
        lastIndex = index

        val acc = calls.getOrPut(index) { CallAcc() }
        if (id.isNotBlank()) acc.id = id

        val function = chunk.optJSONObject("function") ?: return
        val name = function.stringOrNull("name").orEmpty()
        if (name.isNotBlank() && acc.name.isBlank()) {
            acc.name = name
            events += StreamEvent.ToolCallStarted(index, acc.id.ifBlank { "call_$index" }, name)
        }
        function.stringOrNull("arguments")?.let { acc.args.append(it) }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)
}
