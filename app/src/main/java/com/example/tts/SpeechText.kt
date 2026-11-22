package com.example.tts

/** Convierte una respuesta en Markdown en texto natural para leerlo en voz alta (sin deletrear símbolos). */
object SpeechText {
    private val FENCED = Regex("```[\\s\\S]*?(```|$)")
    private val INLINE_CODE = Regex("`([^`\n]*)`")
    private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val URL = Regex("https?://\\S+")
    private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val QUOTE = Regex("(?m)^\\s*>\\s?")
    private val RULE = Regex("(?m)^\\s*([-*_]\\s*){3,}$")
    private val BULLET = Regex("(?m)^\\s*[-*+•]\\s+")
    private val NUMBERED = Regex("(?m)^\\s*(\\d+)[.)]\\s+")
    private val TABLE_SEP = Regex("(?m)^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
    private val EMPHASIS = Regex("(\\*\\*|__|\\*|~~)(.+?)\\1")
    private val HTML = Regex("<[^>]+>")
    private val EMOJI_SYMBOLS = Regex("[\\u2190-\\u21FF\\u2500-\\u27BF\\uFE0F\\u200D\\x{1F000}-\\x{1FAFF}]")
    private val SPACES = Regex("[ \\t]+")
    private val BLANKS = Regex("\\n{2,}")

    /** Máximo de caracteres que se envían (la API cobra por longitud). */
    const val MAX_CHARS = 3000

    fun clean(markdown: String): String {
        var t = markdown
        t = FENCED.replace(t, " ")
        t = IMAGE.replace(t) { it.groupValues[1] }
        t = LINK.replace(t) { it.groupValues[1] }
        t = URL.replace(t, " ")
        t = INLINE_CODE.replace(t) { it.groupValues[1] }
        t = TABLE_SEP.replace(t, "")
        t = t.replace('|', ',')
        t = HEADING.replace(t, "")
        t = QUOTE.replace(t, "")
        t = RULE.replace(t, "")
        t = BULLET.replace(t, "")
        t = NUMBERED.replace(t) { "${it.groupValues[1]}. " }
        repeat(2) { t = EMPHASIS.replace(t) { m -> m.groupValues[2] } }
        t = HTML.replace(t, "")
        t = EMOJI_SYMBOLS.replace(t, "")
        t = SPACES.replace(t, " ")
        t = t.lines().joinToString("\n") { it.trim() }
        t = BLANKS.replace(t, "\n").trim()
        return if (t.length <= MAX_CHARS) t else t.take(MAX_CHARS).substringBeforeLast(' ').trimEnd() + "…"
    }
}
