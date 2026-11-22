package com.example.agent.llm

import com.example.agent.model.StreamEvent
import com.example.agent.model.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SseParserTest {

    private fun parse(vararg lines: String): List<StreamEvent> {
        val p = SseParser()
        return lines.flatMap { p.feed(it) } + p.finish()
    }

    @Test
    fun streamsTextDeltasAndCompletes() {
        val events = parse(
            """data: {"choices":[{"delta":{"role":"assistant","content":"Hola"}}]}""",
            "",
            """data: {"choices":[{"delta":{"content":" mundo"}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            "data: [DONE]"
        )
        assertEquals(
            listOf(
                StreamEvent.TextDelta("Hola"),
                StreamEvent.TextDelta(" mundo"),
                StreamEvent.Completed("stop", emptyList())
            ),
            events
        )
    }

    @Test
    fun ignoresKeepAliveCommentsAndBlankLines() {
        val events = parse(": OPENROUTER PROCESSING", "", "  ", """data: {"choices":[{"delta":{"content":"x"}}]}""")
        assertEquals(StreamEvent.TextDelta("x"), events.first())
    }

    @Test
    fun reasoningFieldsAreEmittedAsReasoning() {
        val events = parse(
            """data: {"choices":[{"delta":{"reasoning":"pienso"}}]}""",
            """data: {"choices":[{"delta":{"reasoning_content":" más"}}]}"""
        )
        assertEquals(StreamEvent.ReasoningDelta("pienso"), events[0])
        assertEquals(StreamEvent.ReasoningDelta(" más"), events[1])
    }

    @Test
    fun nullContentIsNotEmittedAsTheStringNull() {
        val events = parse("""data: {"choices":[{"delta":{"content":null}}]}""")
        assertTrue(events.none { it is StreamEvent.TextDelta })
    }

    @Test
    fun assemblesToolCallArgumentsSplitAcrossChunks() {
        val events = parse(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"write_file","arguments":""}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"path\":\"a."}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"txt\",\"content\":\"hi\"}"}}]}}]}""",
            """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            "data: [DONE]"
        )
        assertEquals(StreamEvent.ToolCallStarted(0, "call_a", "write_file"), events.first())
        val done = events.last() as StreamEvent.Completed
        assertEquals("tool_calls", done.finishReason)
        assertEquals(listOf(ToolCall("call_a", "write_file", """{"path":"a.txt","content":"hi"}""")), done.toolCalls)
    }

    @Test
    fun assemblesSeveralParallelToolCallsByIndex() {
        val events = parse(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c0","function":{"name":"read_file","arguments":"{\"path\":\"a\"}"}},{"index":1,"id":"c1","function":{"name":"list_dir","arguments":"{}"}}]}}]}""",
            "data: [DONE]"
        )
        val calls = (events.last() as StreamEvent.Completed).toolCalls
        assertEquals(listOf("read_file", "list_dir"), calls.map { it.name })
        assertEquals(listOf("c0", "c1"), calls.map { it.id })
    }

    @Test
    fun toolCallsWithoutIndexAreSplitById() {
        val events = parse(
            """data: {"choices":[{"delta":{"tool_calls":[{"id":"x1","function":{"name":"read_file","arguments":"{}"}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"id":"x2","function":{"name":"grep","arguments":"{}"}}]}}]}"""
        )
        assertEquals(listOf("x1", "x2"), (events.last() as StreamEvent.Completed).toolCalls.map { it.id })
    }

    @Test
    fun missingIdGetsGeneratedAndEmptyArgsBecomeEmptyObject() {
        val calls = (parse(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"list_dir"}}]}}]}"""
        ).last() as StreamEvent.Completed).toolCalls
        assertEquals(ToolCall("call_0", "list_dir", "{}"), calls.single())
    }

    @Test
    fun completedIsEmittedOnlyOnce() {
        val p = SseParser()
        val a = p.feed("data: [DONE]")
        val b = p.finish()
        assertEquals(1, a.size)
        assertTrue(b.isEmpty())
    }

    @Test
    fun malformedJsonChunkIsIgnored() {
        val events = parse("data: {not json", """data: {"choices":[{"delta":{"content":"ok"}}]}""")
        assertEquals(StreamEvent.TextDelta("ok"), events.first())
    }

    @Test
    fun midStreamProviderErrorThrows() {
        try {
            parse("""data: {"error":{"message":"rate limited","code":429}}""")
            fail("debía lanzar")
        } catch (e: LlmStreamException) {
            assertEquals("rate limited", e.message)
        }
    }

    @Test
    fun handlesCarriageReturns() {
        val events = parse("data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}\r")
        assertEquals(StreamEvent.TextDelta("a"), events.first())
    }
}
