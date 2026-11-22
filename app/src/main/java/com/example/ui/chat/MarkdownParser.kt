package com.example.ui.chat

/** Bloques de Markdown que la interfaz sabe pintar. Parser puro (sin Compose) para poder testearlo. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val level: Int, val marker: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

object MarkdownParser {
    private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
    private val QUOTE = Regex("""^\s{0,3}>\s?(.*)$""")
    private val RULE = Regex("""^\s{0,3}([-*_])(?:\s*\1){2,}\s*$""")
    private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{2,}:?\s*(?:\|\s*:?-{2,}:?\s*)*\|?\s*$""")

    fun parse(text: String): List<MdBlock> {
        val lines = text.split("\n")
        val blocks = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            when {
                line.isBlank() -> flushParagraph()

                RULE.matches(line) -> { flushParagraph(); blocks += MdBlock.Rule }

                HEADING.matches(line) -> {
                    flushParagraph()
                    val m = HEADING.find(line)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
                }

                isTableStart(lines, i) -> {
                    flushParagraph()
                    val header = splitRow(line)
                    val rows = mutableListOf<List<String>>()
                    i += 2
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        rows += splitRow(lines[i])
                        i++
                    }
                    blocks += MdBlock.Table(header, rows)
                    continue
                }

                QUOTE.matches(line) -> {
                    flushParagraph()
                    val quoted = StringBuilder()
                    while (i < lines.size && QUOTE.matches(lines[i].trimEnd())) {
                        if (quoted.isNotEmpty()) quoted.append('\n')
                        quoted.append(QUOTE.find(lines[i].trimEnd())!!.groupValues[1])
                        i++
                    }
                    blocks += MdBlock.Quote(quoted.toString().trim())
                    continue
                }

                BULLET.matches(line) -> {
                    flushParagraph()
                    val m = BULLET.find(line)!!
                    blocks += MdBlock.ListItem(m.groupValues[1].length / 2, "•", m.groupValues[2])
                }

                ORDERED.matches(line) -> {
                    flushParagraph()
                    val m = ORDERED.find(line)!!
                    blocks += MdBlock.ListItem(m.groupValues[1].length / 2, m.groupValues[2] + ".", m.groupValues[3])
                }

                else -> {
                    // Línea de continuación de un ítem de lista (indentada justo después de él)
                    val last = blocks.lastOrNull()
                    if (paragraph.isEmpty() && last is MdBlock.ListItem && lines[i].startsWith("  ")) {
                        blocks[blocks.lastIndex] = last.copy(text = last.text + "\n" + line.trim())
                    } else {
                        if (paragraph.isNotEmpty()) paragraph.append('\n')
                        paragraph.append(line)
                    }
                }
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    /**
     * Ancho (dp) de cada columna, igual en todas las filas. Se estima por el texto más largo de la
     * columna (con un tope para que las celdas largas se partan en varias líneas en vez de ensanchar
     * la tabla sin límite).
     */
    fun tableColumnWidthsDp(table: MdBlock.Table): List<Int> {
        val rows = listOf(table.header) + table.rows
        val columns = rows.maxOfOrNull { it.size } ?: 0
        return List(columns) { col ->
            val longest = rows.maxOfOrNull { visibleLength(it.getOrNull(col).orEmpty()) } ?: 0
            (longest * 7.4 + 22).toInt().coerceIn(MIN_COL_DP, MAX_COL_DP)
        }
    }

    const val MIN_COL_DP = 64
    const val MAX_COL_DP = 220

    private fun visibleLength(cell: String): Int = cell.replace(Regex("""[*_`~]|\[|]\([^)]*\)"""), "").length

    private fun isTableStart(lines: List<String>, i: Int): Boolean =
        lines[i].contains('|') && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1]) && lines[i + 1].contains('-')

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}
