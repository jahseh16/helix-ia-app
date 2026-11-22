package com.example.agent.llm

import com.example.agent.model.LlmMessage
import com.example.agent.model.LlmRequest
import org.json.JSONArray
import org.json.JSONObject

/** Construye el cuerpo JSON de `/chat/completions` (formato OpenAI, válido para OpenRouter y Groq). */
object OpenAiRequestBuilder {

    fun build(request: LlmRequest, stream: Boolean = true): JSONObject = JSONObject().apply {
        put("model", request.model)
        put("stream", stream)
        put("messages", JSONArray().also { arr -> request.messages.forEach { arr.put(messageToJson(it)) } })
        if (request.tools.isNotEmpty()) {
            put("tools", JSONArray().also { arr ->
                request.tools.forEach { t ->
                    arr.put(
                        JSONObject()
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", t.name)
                                    .put("description", t.description)
                                    .put("parameters", t.parameters)
                            )
                    )
                }
            })
            put("tool_choice", "auto")
        }
    }

    fun messageToJson(m: LlmMessage): JSONObject = when (m) {
        is LlmMessage.System -> JSONObject().put("role", "system").put("content", m.content)
        is LlmMessage.User -> JSONObject().put("role", "user").put("content", m.content)
        is LlmMessage.Tool -> JSONObject()
            .put("role", "tool")
            .put("tool_call_id", m.toolCallId)
            .put("content", m.content)
        is LlmMessage.Assistant -> JSONObject().apply {
            put("role", "assistant")
            // OpenAI exige `content` nulo (no cadena vacía) cuando solo hay tool_calls
            put("content", if (m.content.isBlank() && m.toolCalls.isNotEmpty()) JSONObject.NULL else m.content)
            if (m.toolCalls.isNotEmpty()) {
                put("tool_calls", JSONArray().also { arr ->
                    m.toolCalls.forEach { c ->
                        arr.put(
                            JSONObject()
                                .put("id", c.id)
                                .put("type", "function")
                                .put("function", JSONObject().put("name", c.name).put("arguments", c.argumentsJson))
                        )
                    }
                })
            }
        }
    }
}
