package com.example.ui

import com.example.ui.chat.SyntaxHighlighter
import com.example.ui.chat.SyntaxToken
import com.example.ui.chat.TokenKind
import com.example.ui.chat.TokenKind.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    /** Pares (texto, tipo) para leer los tests sin contar índices. */
    private fun tokens(code: String, lang: String): List<Pair<String, TokenKind>> =
        SyntaxHighlighter.tokenize(code, lang).map { code.substring(it.start, it.end) to it.kind }

    private fun kindOf(code: String, lang: String, text: String): TokenKind? =
        tokens(code, lang).firstOrNull { it.first == text }?.second

    @Test fun kotlin_keywordsStringsNumbersCommentsAndTypes() {
        val code = "fun greet(name: String): Int { // saluda\n  val x = 42\n  return \"hola\".length }"
        assertEquals(KEYWORD, kindOf(code, "kotlin", "fun"))
        assertEquals(FUNCTION, kindOf(code, "kotlin", "greet"))
        assertEquals(TYPE, kindOf(code, "kotlin", "String"))
        assertEquals(TYPE, kindOf(code, "kotlin", "Int"))
        assertEquals(COMMENT, kindOf(code, "kotlin", "// saluda"))
        assertEquals(NUMBER, kindOf(code, "kotlin", "42"))
        assertEquals(STRING, kindOf(code, "kotlin", "\"hola\""))
        assertEquals(KEYWORD, kindOf(code, "kotlin", "return"))
    }

    @Test fun commentMarkersInsideStrings_areNotComments() {
        val t = tokens("""val url = "http://x.com/a" // fin""", "kotlin")
        assertTrue(t.contains("\"http://x.com/a\"" to STRING))
        assertTrue(t.contains("// fin" to COMMENT))
        assertEquals(1, t.count { it.second == COMMENT })
    }

    @Test fun keywordsInsideStringsOrComments_areNotHighlightedSeparately() {
        val t = tokens("""x = "if else for" # return""", "python")
        assertEquals(listOf("\"if else for\"" to STRING, "# return" to COMMENT), t)
    }

    @Test fun blockComments_andUnterminatedOnesRunToTheEnd() {
        assertEquals(listOf("/* a\nb */" to COMMENT), tokens("/* a\nb */", "java"))
        assertEquals(listOf("/* sin cerrar\nfun" to COMMENT), tokens("/* sin cerrar\nfun", "kotlin"))
    }

    @Test fun unterminatedString_endsAtTheLine_notTheWholeFile() {
        val t = tokens("val a = \"abierto\nval b = 1", "kotlin")
        assertEquals(STRING, t.first { it.first.startsWith("\"abierto") }.second)
        assertTrue(t.contains("1" to NUMBER))
        assertTrue(t.contains("val" to KEYWORD))
    }

    @Test fun python_tripleQuotes_literals_decorators() {
        val code = "@dataclass\nclass A:\n    \"\"\"doc\n    larga\"\"\"\n    x = None\n"
        assertEquals(ANNOTATION, kindOf(code, "python", "@dataclass"))
        assertEquals(KEYWORD, kindOf(code, "py", "class"))
        assertEquals(STRING, kindOf(code, "python", "\"\"\"doc\n    larga\"\"\""))
        assertEquals(LITERAL, kindOf(code, "python", "None"))
    }

    @Test fun bash_commentsVariablesAndKeywords() {
        val code = "for f in *.txt; do echo \"\$f\" # listar\ndone"
        assertEquals(KEYWORD, kindOf(code, "bash", "for"))
        assertEquals(KEYWORD, kindOf(code, "sh", "done"))
        assertEquals(COMMENT, kindOf(code, "bash", "# listar"))
        assertEquals(STRING, kindOf(code, "bash", "\"\$f\""))
        assertEquals(FUNCTION, kindOf("echo \$HOME \${PATH}", "bash", "\$HOME"))
    }

    @Test fun json_distinguishesKeysFromValues() {
        val code = """{"name": "Helix", "n": 3, "ok": true, "x": null}"""
        assertEquals(KEY, kindOf(code, "json", "\"name\""))
        assertEquals(STRING, kindOf(code, "json", "\"Helix\""))
        assertEquals(NUMBER, kindOf(code, "json", "3"))
        assertEquals(LITERAL, kindOf(code, "json", "true"))
        assertEquals(LITERAL, kindOf(code, "json", "null"))
    }

    @Test fun html_tagsAttributesStringsAndComments() {
        val code = """<!-- nota --><div class="a">hola</div>"""
        assertEquals(COMMENT, kindOf(code, "html", "<!-- nota -->"))
        assertEquals(KEYWORD, kindOf(code, "html", "<div"))
        assertEquals(FUNCTION, kindOf(code, "html", "class"))
        assertEquals(STRING, kindOf(code, "html", "\"a\""))
        assertEquals(KEYWORD, kindOf(code, "xml", "</div"))
    }

    @Test fun yaml_keys() {
        val t = tokens("name: helix\nenabled: true # ok", "yaml")
        assertTrue(t.contains("name" to KEY))
        assertTrue(t.contains("enabled" to KEY))
        assertTrue(t.contains("true" to LITERAL))
        assertTrue(t.contains("# ok" to COMMENT))
    }

    @Test fun sql_isCaseInsensitiveForCommonKeywords() {
        assertEquals(KEYWORD, kindOf("SELECT * FROM t -- fin", "sql", "SELECT"))
        assertEquals(KEYWORD, kindOf("select * from t", "sql", "from"))
        assertEquals(COMMENT, kindOf("SELECT 1 -- fin", "sql", "-- fin"))
    }

    @Test fun numbers_hexFloatsAndExponents_butNotInsideIdentifiers() {
        val t = tokens("a1 = 0xFF + 3.14 + 2e10", "javascript")
        assertTrue(t.contains("0xFF" to NUMBER))
        assertTrue(t.contains("3.14" to NUMBER))
        assertTrue(t.contains("2e10" to NUMBER))
        assertTrue(t.none { it.first == "1" })
    }

    @Test fun constants_andPascalCaseTypes() {
        assertEquals(LITERAL, kindOf("MAX_SIZE = 10", "python", "MAX_SIZE"))
        assertEquals(TYPE, kindOf("val m: HashMap<A, B>", "kotlin", "HashMap"))
    }

    @Test fun unknownLanguage_stillHighlightsStringsAndComments_andNeverThrows() {
        val t = tokens("if (x) { return \"a\" } // z", "lenguaje-raro")
        assertTrue(t.contains("\"a\"" to STRING))
        assertTrue(t.contains("// z" to COMMENT))
        assertEquals(emptyList<SyntaxToken>(), SyntaxHighlighter.tokenize("", "kotlin"))
    }

    @Test fun tokensNeverOverlap_andStayInsideTheText() {
        val code = "fun main() {\n  val s = \"a // b\" /* c */\n  // d \"e\"\n  println(1 + 0x2)\n}"
        val toks = SyntaxHighlighter.tokenize(code, "kotlin")
        toks.zipWithNext().forEach { (a, b) -> assertTrue("$a solapa con $b", a.end <= b.start) }
        assertTrue(toks.all { it.start >= 0 && it.end <= code.length && it.start < it.end })
    }

    @Test fun veryLargeInput_isNotHighlighted_toProtectPerformance() {
        assertTrue(SyntaxHighlighter.tokenize("val a = 1\n".repeat(5000), "kotlin").isEmpty())
    }

    @Test fun highlight_keepsTheOriginalTextExactly() {
        val code = "val x = \"hola\" // c"
        assertEquals(code, SyntaxHighlighter.highlight(code, "kotlin").text)
        assertTrue(SyntaxHighlighter.highlight(code, "kotlin").spanStyles.isNotEmpty())
    }
}
