package com.example.ui

import com.example.ui.chat.MarkdownParser
import com.example.ui.chat.MdBlock
import com.example.ui.chat.MessagePart
import com.example.ui.chat.buildMarkdownAnnotatedString
import com.example.ui.chat.parseMessageText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownParserTest {

    private fun parse(s: String) = MarkdownParser.parse(s)

    @Test fun headings_levelsAndTrailingHashes() {
        assertEquals(
            listOf(MdBlock.Heading(1, "Título"), MdBlock.Heading(3, "Sub"), MdBlock.Heading(2, "Cierre")),
            parse("# Título\n### Sub\n## Cierre ##")
        )
    }

    @Test fun paragraphs_areSeparatedByBlankLines_andKeepSoftBreaks() {
        assertEquals(
            listOf(MdBlock.Paragraph("uno\ndos"), MdBlock.Paragraph("tres")),
            parse("uno\ndos\n\ntres")
        )
    }

    @Test fun bulletAndOrderedLists_withNesting() {
        val blocks = parse("- a\n  - b\n1. uno\n2) dos")
        assertEquals(
            listOf(
                MdBlock.ListItem(0, "•", "a"),
                MdBlock.ListItem(1, "•", "b"),
                MdBlock.ListItem(0, "1.", "uno"),
                MdBlock.ListItem(0, "2.", "dos")
            ),
            blocks
        )
    }

    @Test fun boldAtLineStart_isNotMistakenForABullet() {
        assertEquals(listOf(MdBlock.Paragraph("**negrita** al inicio")), parse("**negrita** al inicio"))
    }

    @Test fun listItemContinuationLines_areAttachedToTheItem() {
        assertEquals(listOf(MdBlock.ListItem(0, "•", "uno\ncontinúa")), parse("- uno\n  continúa"))
    }

    @Test fun quotes_mergeConsecutiveLines() {
        assertEquals(listOf(MdBlock.Quote("a\nb"), MdBlock.Paragraph("fuera")), parse("> a\n> b\nfuera"))
    }

    @Test fun horizontalRule() {
        assertEquals(listOf(MdBlock.Paragraph("a"), MdBlock.Rule, MdBlock.Paragraph("b")), parse("a\n\n---\n\nb"))
    }

    @Test fun tables_headerAndRows() {
        val t = parse("| Nombre | Edad |\n|---|:--:|\n| Ana | 30 |\n| Luis | 25 |\ntexto") 
        val table = t[0] as MdBlock.Table
        assertEquals(listOf("Nombre", "Edad"), table.header)
        assertEquals(listOf(listOf("Ana", "30"), listOf("Luis", "25")), table.rows)
        assertEquals(MdBlock.Paragraph("texto"), t[1])
    }

    @Test fun aPipeWithoutSeparatorRow_isJustText() {
        assertEquals(listOf(MdBlock.Paragraph("a | b | c")), parse("a | b | c"))
    }

    @Test fun emptyInput() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("\n\n  \n").isEmpty())
    }

    // ---- inline
    private fun inline(s: String) = buildMarkdownAnnotatedString(s, Color.White)

    @Test fun inline_boldItalicStrikeAndCode() {
        val r = inline("**negrita** *cursiva* ~~tachado~~ `código`")
        assertEquals("negrita cursiva tachado código", r.text)
        assertTrue(r.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(r.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertEquals(4, r.spanStyles.size)
    }

    @Test fun inline_nestedBoldWithCode() {
        val r = inline("**mira `x` aquí**")
        assertEquals("mira x aquí", r.text)
    }

    @Test fun inline_snakeCaseAndMathAreNotItalics() {
        assertEquals("mi_variable_larga", inline("mi_variable_larga").text)
        assertEquals("2 * 3 * 4", inline("2 * 3 * 4").text)
        assertTrue(inline("mi_variable_larga").spanStyles.isEmpty())
    }

    @Test fun inline_links_keepOnlyTheLabel_andOnlyHttp() {
        val r = inline("ver [la doc](https://example.com/a?b=1) y [mal](javascript:x)")
        assertEquals("ver la doc y [mal](javascript:x)", r.text)
        assertEquals(1, r.getLinkAnnotations(0, r.length).size)
    }

    @Test fun inline_unclosedMarkersStayLiteral_duringStreaming() {
        assertEquals("**sin cerrar", inline("**sin cerrar").text)
        assertEquals("`abierto", inline("`abierto").text)
        assertEquals("[texto](http", inline("[texto](http").text)
    }

    // ---- bloques de código
    @Test fun codeFence_unclosedBlock_isCodeUntilTheEnd() {
        val parts = parseMessageText("Mira:\n```kotlin\nval x = 1\nval y")
        assertEquals(MessagePart.TextBlock("Mira:"), parts[0])
        assertEquals(MessagePart.CodeBlock("val x = 1\nval y", "kotlin"), parts[1])
    }

    @Test fun codeFence_severalBlocks_keepLanguageAndProseBetween() {
        val parts = parseMessageText("a\n```bash\nls\n```\nb\n```python\nprint(1)\n```\nc")
        assertEquals(
            listOf(
                MessagePart.TextBlock("a"), MessagePart.CodeBlock("ls", "bash"),
                MessagePart.TextBlock("b"), MessagePart.CodeBlock("print(1)", "python"),
                MessagePart.TextBlock("c")
            ),
            parts
        )
    }

    @Test fun codeFence_withoutLanguage_andIndentedFence() {
        val parts = parseMessageText("  ```\nx\n  ```")
        assertEquals(listOf<MessagePart>(MessagePart.CodeBlock("x", "")), parts)
    }

    @Test fun codeInsideTheBlock_isNeverParsedAsMarkdown() {
        val parts = parseMessageText("```md\n# no es título\n- ni lista\n```")
        assertEquals(MessagePart.CodeBlock("# no es título\n- ni lista", "md"), parts.single())
    }

    @Test fun tableColumns_haveOneWidthPerColumn_withinBounds() {
        val t = com.example.ui.chat.MdBlock.Table(
            listOf("Juego", "Descripción"),
            listOf(listOf("Ark", "Un juego de supervivencia con dinosaurios muy largo ".repeat(4)), listOf("Rust", "PvP"))
        )
        val w = com.example.ui.chat.MarkdownParser.tableColumnWidthsDp(t)
        assertEquals(2, w.size)
        assertEquals(com.example.ui.chat.MarkdownParser.MAX_COL_DP, w[1])
        assertTrue(w[0] >= com.example.ui.chat.MarkdownParser.MIN_COL_DP && w[0] < w[1])
    }

    @Test fun proseBlocks_wrapInsteadOfScrolling() {
        assertTrue(com.example.ui.chat.isProseLanguage("text"))
        assertTrue(com.example.ui.chat.isProseLanguage(" Markdown "))
        assertFalse(com.example.ui.chat.isProseLanguage("kotlin"))
        assertFalse(com.example.ui.chat.isProseLanguage(""))
    }
}
