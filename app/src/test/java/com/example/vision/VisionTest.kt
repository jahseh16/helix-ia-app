package com.example.vision

import com.example.agent.runtime.HistoryMapper
import com.example.agent.model.LlmMessage
import com.example.data.database.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VisionTest {

    @Test
    fun scaleKeepsAspectAndNeverUpscales() {
        assertEquals(1024 to 768, ImageScale.target(4000, 3000))
        assertEquals(768 to 1024, ImageScale.target(3000, 4000))
        assertEquals(500 to 300, ImageScale.target(500, 300))
    }

    @Test
    fun sampleSizeNeverGoesBelowMax() {
        assertEquals(1, ImageScale.sampleSize(1500, 1000))
        assertEquals(4, ImageScale.sampleSize(4000, 4000))
    }

    @Test
    fun parseAnswerStripsThinkBlock() {
        val json = """{"choices":[{"message":{"content":"<think>hmm</think> Es rojo."}}]}"""
        assertEquals("Es rojo.", VlmRunClient.parseAnswer(json))
    }

    @Test
    fun parseAnswerFailsOnEmpty() {
        try {
            VlmRunClient.parseAnswer("""{"choices":[{"message":{"content":"  "}}]}""")
            fail()
        } catch (e: VlmException) {
            assertTrue(e.message!!.contains("descripción"))
        }
    }

    @Test
    fun errorMessagesAreActionable() {
        assertTrue(VlmRunClient.errorFor(429, "", hasKey = false).contains("límite"))
        assertTrue(VlmRunClient.errorFor(401, "", hasKey = true).contains("API key"))
    }

    @Test
    fun historyIncludesImageAnalysisAsText() {
        val row = ChatMessageEntity(sender = ChatMessageEntity.SENDER_USER, text = "¿qué es?", imagePath = "/x.jpg", imageAnalysis = "un gato")
        val user = HistoryMapper.toLlmMessages(listOf(row)).single() as LlmMessage.User
        assertTrue(user.content.contains("¿qué es?"))
        assertTrue(user.content.contains("un gato"))
        assertTrue(!user.content.contains("base64"))
    }
}
