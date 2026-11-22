package com.example.agent.llm

import com.example.agent.model.LlmMessage
import com.example.agent.model.LlmRequest
import com.example.agent.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRequestBuilderTest {
    @Test fun assistantWithText_keepsContentString() {
        val json = OpenAiRequestBuilder.messageToJson(LlmMessage.Assistant("Voy a leer", listOf(ToolCall("1", "read_file", "{}"))))
        assertEquals("Voy a leer", json.getString("content"))
        assertEquals(1, json.getJSONArray("tool_calls").length())
    }

    @Test fun plainAssistant_hasNoToolCallsField() {
        val json = OpenAiRequestBuilder.messageToJson(LlmMessage.Assistant("hola"))
        assertTrue(!json.has("tool_calls"))
    }

    @Test fun reasoningIsNeverSentBackToTheProvider() {
        val json = OpenAiRequestBuilder.messageToJson(LlmMessage.Assistant("r", reasoning = "pensamiento privado"))
        assertTrue(!json.toString().contains("pensamiento privado"))
    }

    @Test fun nonStreamingFlagIsHonoured() {
        assertEquals(false, OpenAiRequestBuilder.build(LlmRequest("m", emptyList()), stream = false).getBoolean("stream"))
    }
}
