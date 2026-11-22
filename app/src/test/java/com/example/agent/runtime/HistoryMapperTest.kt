package com.example.agent.runtime

import com.example.agent.model.LlmMessage
import com.example.agent.model.ToolCall
import com.example.data.database.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMapperTest {

    private fun user(t: String) = ChatMessageEntity(sender = ChatMessageEntity.SENDER_USER, text = t)
    private fun ai(t: String, calls: List<ToolCall> = emptyList()) =
        ChatMessageEntity(sender = ChatMessageEntity.SENDER_AI, text = t, toolCallsJson = HistoryMapper.toolCallsToJson(calls))
    private fun tool(id: String, name: String, out: String) =
        ChatMessageEntity(sender = ChatMessageEntity.SENDER_TOOL, text = out, toolCallId = id, toolName = name, toolStatus = "OK")

    @Test fun mapsTheWholeConversation_inOrder() {
        val call = ToolCall("c1", "read_file", """{"path":"a"}""")
        val msgs = HistoryMapper.toLlmMessages(
            listOf(user("hola"), ai("hola, ¿qué tal?"), user("lee a"), ai("", listOf(call)), tool("c1", "read_file", "contenido"), ai("Listo"))
        )
        assertEquals(
            listOf(
                LlmMessage.User("hola"),
                LlmMessage.Assistant("hola, ¿qué tal?"),
                LlmMessage.User("lee a"),
                LlmMessage.Assistant("", listOf(call)),
                LlmMessage.Tool("c1", "read_file", "contenido"),
                LlmMessage.Assistant("Listo")
            ),
            msgs
        )
    }

    @Test fun uiOnlyRows_areExcluded() {
        val msgs = HistoryMapper.toLlmMessages(
            listOf(
                user("a"),
                ChatMessageEntity(sender = ChatMessageEntity.SENDER_SYSTEM, text = "aviso"),
                ChatMessageEntity(sender = ChatMessageEntity.SENDER_ERROR, text = "fallo"),
                ai("b")
            )
        )
        assertEquals(listOf<LlmMessage>(LlmMessage.User("a"), LlmMessage.Assistant("b")), msgs)
    }

    @Test fun cancelledToolCall_getsASyntheticResult_soTheApiAcceptsTheHistory() {
        val call = ToolCall("c1", "write_file", "{}")
        val msgs = HistoryMapper.toLlmMessages(listOf(user("crea"), ai("", listOf(call))))
        assertEquals(LlmMessage.Tool("c1", "write_file", HistoryMapper.CANCELLED_TOOL_RESULT), msgs.last())
    }

    @Test fun everyToolCallIsFollowedByItsToolMessage() {
        val calls = listOf(ToolCall("a", "read_file", "{}"), ToolCall("b", "list_dir", "{}"))
        val msgs = HistoryMapper.toLlmMessages(listOf(user("x"), ai("", calls), tool("b", "list_dir", "B")))
        assertEquals(listOf("a", "b"), msgs.filterIsInstance<LlmMessage.Tool>().map { it.toolCallId })
        assertEquals("B", (msgs.last() as LlmMessage.Tool).content)
    }

    @Test fun limit_dropsOldestMessages_startingAtAUserTurn() {
        val rows = mutableListOf<ChatMessageEntity>()
        repeat(10) { rows += user("u$it " + "x".repeat(100)); rows += ai("a$it " + "y".repeat(100)) }
        val msgs = HistoryMapper.toLlmMessages(rows, maxChars = 600)
        assertTrue(msgs.size < 20)
        assertTrue("debe empezar en un turno de usuario", msgs.first() is LlmMessage.User)
        assertTrue((msgs.last() as LlmMessage.Assistant).content.startsWith("a9"))
    }

    @Test fun limit_neverLeavesAnOrphanToolMessage() {
        val call = ToolCall("c", "read_file", "{}")
        val rows = listOf(user("viejo " + "z".repeat(500)), ai("", listOf(call)), tool("c", "read_file", "r".repeat(500)), user("nuevo"), ai("fin"))
        val msgs = HistoryMapper.toLlmMessages(rows, maxChars = 100)
        assertTrue(msgs.first() is LlmMessage.User)
        assertEquals(LlmMessage.User("nuevo"), msgs.first())
    }

    @Test fun toolCallsJson_roundTrips_andToleratesGarbage() {
        val calls = listOf(ToolCall("1", "edit_file", """{"old_string":"a\"b"}"""))
        assertEquals(calls, HistoryMapper.parseToolCalls(HistoryMapper.toolCallsToJson(calls)))
        assertTrue(HistoryMapper.parseToolCalls("no es json").isEmpty())
        assertTrue(HistoryMapper.parseToolCalls(null).isEmpty())
    }
}
