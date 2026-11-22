package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

private val LINK = Regex("""^\[([^\]\n]+)]\((https?://[^)\s]+)\)""")

/**
 * Markdown inline: `código`, **negrita**, *cursiva* / _cursiva_, ~~tachado~~ y [enlaces](https://…).
 * Un marcador sin cierre se deja literal (importante durante el streaming, cuando el cierre aún no llegó).
 */
fun buildMarkdownAnnotatedString(text: String, defaultColor: Color, isUser: Boolean = false): AnnotatedString =
    buildAnnotatedString { appendInline(text, defaultColor, isUser) }

private fun AnnotatedString.Builder.appendInline(text: String, color: Color, isUser: Boolean) {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i && !text.substring(i + 1, end).contains('\n')) {
                    withStyle(
                        SpanStyle(
                            fontFamily = UiMono,
                            fontSize = 13.sp,
                            color = if (isUser) Color.White else AppCyan,
                            background = Color.White.copy(alpha = 0.10f)
                        )
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(c); i++ }
            }

            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(text.substring(i + 2, end), color, isUser) }
                    i = end + 2
                } else { append(marker); i += 2 }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(text.substring(i + 2, end), color, isUser) }
                    i = end + 2
                } else { append("~~"); i += 2 }
            }

            (c == '*' || c == '_') && canOpenItalic(text, i) -> {
                val end = findItalicEnd(text, i + 1, c)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(text.substring(i + 1, end), color, isUser) }
                    i = end + 1
                } else { append(c); i++ }
            }

            c == '[' -> {
                val m = LINK.find(text.substring(i))
                if (m != null) {
                    withLink(
                        LinkAnnotation.Url(
                            m.groupValues[2],
                            TextLinkStyles(SpanStyle(color = if (isUser) Color.White else AppAccentSoft, textDecoration = TextDecoration.Underline))
                        )
                    ) { append(m.groupValues[1]) }
                    i += m.value.length
                } else { append(c); i++ }
            }

            else -> { append(c); i++ }
        }
    }
}

/** `*` o `_` abren cursiva solo si van pegados a una palabra (evita `snake_case` y `2 * 3`). */
private fun canOpenItalic(text: String, i: Int): Boolean {
    val next = text.getOrNull(i + 1) ?: return false
    if (next.isWhitespace() || next == text[i]) return false
    if (text[i] == '_') {
        val prev = text.getOrNull(i - 1)
        if (prev != null && prev.isLetterOrDigit()) return false
    }
    return true
}

private fun findItalicEnd(text: String, from: Int, marker: Char): Int {
    var j = from
    while (j < text.length) {
        if (text[j] == '\n') return -1
        if (text[j] == marker && !text[j - 1].isWhitespace() && text.getOrNull(j + 1) != marker) {
            if (marker == '_' && text.getOrNull(j + 1)?.isLetterOrDigit() == true) { j++; continue }
            return j
        }
        j++
    }
    return -1
}

/** Pinta el Markdown de un fragmento de texto (sin bloques de código: esos los separa [parseMessageText]). */
@Composable
fun MarkdownText(text: String, isUser: Boolean, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val base = if (isUser) Color.White else AppText

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) { 1 -> 24.sp; 2 -> 21.sp; 3 -> 18.sp; else -> 16.sp }
                    Text(
                        text = remember(block.text) { buildMarkdownAnnotatedString(block.text, base, isUser) },
                        style = BodyStyle.copy(color = base, fontSize = size, lineHeight = size * 1.3f, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 0.dp)
                    )
                }

                is MdBlock.Paragraph -> Text(
                    text = remember(block.text) { buildMarkdownAnnotatedString(block.text, base, isUser) },
                    style = BodyStyle.copy(color = base, lineHeight = 22.sp)
                )

                is MdBlock.ListItem -> Row(modifier = Modifier.padding(start = (block.level * 18).dp)) {
                    Text(
                        text = block.marker,
                        style = BodyStyle.copy(color = if (isUser) Color.White else AppAccentSoft, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.width(if (block.marker == "•") 18.dp else 26.dp)
                    )
                    Text(
                        text = remember(block.text) { buildMarkdownAnnotatedString(block.text, base, isUser) },
                        style = BodyStyle.copy(color = base, lineHeight = 22.sp),
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(AppAccent, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = remember(block.text) { buildMarkdownAnnotatedString(block.text, AppTextMuted, isUser) },
                        style = BodyStyle.copy(color = if (isUser) Color.White.copy(alpha = 0.9f) else AppTextMuted, lineHeight = 21.sp, fontStyle = FontStyle.Italic)
                    )
                }

                MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(AppStroke))

                is MdBlock.Table -> MarkdownTable(block, base, isUser)
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table, base: Color, isUser: Boolean) {
    val widths = remember(table) { MarkdownParser.tableColumnWidthsDp(table) }
    val rows = remember(table) { listOf(table.header) + table.rows }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, AppStroke, RoundedCornerShape(14.dp))
            .background(AppSurface.copy(alpha = if (isUser) 0.25f else 1f))
    ) {
        // Cada columna tiene el MISMO ancho en todas las filas: nada se aplasta ni se desalinea.
        // Si la tabla no cabe, se desplaza en horizontal dentro de su marco (la página no se corta).
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Box(Modifier.width(widths.sum().dp).height(1.dp).background(AppStroke.copy(alpha = 0.6f)))
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(if (rowIndex == 0) AppSurface3.copy(alpha = 0.6f) else Color.Transparent)
                ) {
                    widths.forEachIndexed { col, w ->
                        Text(
                            text = remember(row.getOrNull(col)) { buildMarkdownAnnotatedString(row.getOrNull(col).orEmpty(), base, isUser) },
                            style = BodyStyle.copy(
                                color = base, fontSize = 13.sp, lineHeight = 18.sp,
                                fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier.width(w.dp).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
