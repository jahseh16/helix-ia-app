package com.example.ui

import androidx.compose.ui.graphics.Color
import com.example.ui.chat.MessagePart
import com.example.ui.chat.buildMarkdownAnnotatedString
import com.example.ui.chat.parseMessageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageParsingTest {

    @Test
    fun plainText_isSingleTextBlock() {
        val parts = parseMessageText("hola mundo")
        assertEquals(listOf(MessagePart.TextBlock("hola mundo")), parts)
    }

    @Test
    fun fencedCode_isSplitIntoTextAndCodeBlocks() {
        val parts = parseMessageText("antes\n```kotlin\nval x = 1\n```\ndespués")
        assertEquals(3, parts.size)
        assertTrue(parts[0] is MessagePart.TextBlock)
        assertEquals(MessagePart.CodeBlock("val x = 1", "kotlin"), parts[1])
        assertTrue(parts[2] is MessagePart.TextBlock)
    }

    @Test
    fun filesBlock_keepsItsLanguageTag() {
        val parts = parseMessageText("```files\n1. a.txt\n```")
        assertEquals(MessagePart.CodeBlock("1. a.txt", "files"), parts.single())
    }

    @Test
    fun emptyText_producesNoParts() {
        assertTrue(parseMessageText("").isEmpty())
    }

    @Test
    fun markdown_boldAndInlineCode_keepContentWithoutMarkers() {
        val result = buildMarkdownAnnotatedString("**hola** y `code`", Color.White)
        assertEquals("hola y code", result.text)
        assertEquals(2, result.spanStyles.size)
    }

    @Test
    fun markdown_unclosedMarkers_areKeptLiterally() {
        val result = buildMarkdownAnnotatedString("**sin cerrar", Color.White)
        assertEquals("**sin cerrar", result.text)
    }
}
