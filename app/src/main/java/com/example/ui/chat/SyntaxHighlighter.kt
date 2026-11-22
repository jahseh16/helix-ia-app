package com.example.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.example.ui.theme.*

enum class TokenKind { COMMENT, STRING, KEY, NUMBER, KEYWORD, TYPE, LITERAL, ANNOTATION, FUNCTION }

data class SyntaxToken(val start: Int, val end: Int, val kind: TokenKind)

/**
 * Tokenizador de una sola pasada: una regex maestra con alternancia ordenada (comentario | cadena |
 * ... | identificador) evita que un `//` dentro de un string o un número dentro de un comentario se
 * coloreen dos veces. La tokenización no depende de Compose y es testeable en JVM.
 */
object SyntaxHighlighter {

    /** Por encima de este tamaño no se resalta (protege el rendimiento al hacer streaming). */
    private const val MAX_HIGHLIGHT_CHARS = 30_000

    private class Rule(val kind: TokenKind?, val pattern: String) // kind == null → identificador

    private class Spec(
        val lineComments: List<String> = listOf("//"),
        val blockComment: Pair<String, String>? = "/*" to "*/",
        val keywords: Set<String> = emptySet(),
        val literals: Set<String> = setOf("true", "false", "null"),
        val tripleQuotes: Boolean = false,
        val backticks: Boolean = false,
        val annotations: Boolean = false,
        val shellVars: Boolean = false,
        val json: Boolean = false,
        val yaml: Boolean = false,
        val markup: Boolean = false
    ) {
        val regex: Regex by lazy { buildRegex(this) }
        val rules: List<Rule> by lazy { buildRules(this) }
    }

    private fun words(s: String) = s.split(' ', '\n').filter { it.isNotBlank() }.toSet()

    private val KOTLIN = Spec(
        keywords = words("fun val var class object interface enum data sealed abstract open override private public protected internal " +
            "if else when for while do return break continue in is as by import package try catch finally throw this super " +
            "companion init lateinit const suspend inline typealias where out reified operator infix vararg crossinline noinline"),
        literals = setOf("true", "false", "null", "it"), tripleQuotes = true, annotations = true
    )
    private val JAVA = Spec(
        keywords = words("class interface enum extends implements public private protected static final abstract void new if else for while do " +
            "return break continue switch case default try catch finally throw throws import package this super instanceof synchronized volatile " +
            "int long double float boolean char byte short var record sealed permits"),
        annotations = true
    )
    private val JS = Spec(
        keywords = words("function const let var class extends new return if else for while do break continue switch case default try catch " +
            "finally throw import export from as async await yield typeof instanceof in of delete void this super static get set " +
            "interface type enum implements public private protected readonly namespace declare"),
        literals = setOf("true", "false", "null", "undefined", "NaN"), backticks = true
    )
    private val PYTHON = Spec(
        lineComments = listOf("#"), blockComment = null,
        keywords = words("def class return if elif else for while break continue import from as try except finally raise with lambda pass yield " +
            "global nonlocal in is not and or assert del async await match case"),
        literals = setOf("True", "False", "None", "self"), tripleQuotes = true, annotations = true
    )
    private val BASH = Spec(
        lineComments = listOf("#"), blockComment = null,
        keywords = words("if then else elif fi for while until do done case esac in function select return exit break continue " +
            "export local readonly declare source alias unset set shift trap eval exec"),
        literals = setOf("true", "false"), shellVars = true
    )
    private val C = Spec(
        keywords = words("if else for while do return break continue switch case default struct union enum typedef class namespace template " +
            "public private protected static const void int long short char float double unsigned signed bool new delete using include define " +
            "extern inline virtual override auto sizeof try catch throw operator this"),
        literals = setOf("true", "false", "NULL", "nullptr")
    )
    private val GO = Spec(
        keywords = words("func var const type struct interface map chan package import return if else for range switch case default break continue " +
            "go defer select fallthrough goto"),
        literals = setOf("true", "false", "nil", "iota"), backticks = true
    )
    private val RUST = Spec(
        keywords = words("fn let mut const static struct enum impl trait pub use mod crate self super match if else for while loop return break " +
            "continue as in where async await move ref type dyn unsafe extern"),
        literals = setOf("true", "false", "None", "Some", "Ok", "Err")
    )
    private val SWIFT = Spec(
        keywords = words("func let var class struct enum protocol extension import return if else guard for while repeat switch case default break " +
            "continue in is as try catch throw throws init self super static private public internal fileprivate open override async await"),
        literals = setOf("true", "false", "nil"), annotations = true
    )
    private val SQL = Spec(
        lineComments = listOf("--"),
        keywords = words("select from where insert into values update set delete create table drop alter add join left right inner outer full on " +
            "group by order having limit offset as and or not in is like between distinct union all primary key foreign references index " +
            "SELECT FROM WHERE INSERT INTO VALUES UPDATE SET DELETE CREATE TABLE DROP ALTER ADD JOIN LEFT RIGHT INNER OUTER FULL ON GROUP BY " +
            "ORDER HAVING LIMIT OFFSET AS AND OR NOT IN IS LIKE BETWEEN DISTINCT UNION ALL PRIMARY KEY FOREIGN REFERENCES INDEX"),
        literals = setOf("true", "false", "null", "NULL", "TRUE", "FALSE")
    )
    private val CSS = Spec(
        lineComments = emptyList(),
        keywords = words("important media import keyframes font-face supports"),
        literals = emptySet()
    )
    private val JSON = Spec(lineComments = emptyList(), blockComment = null, json = true)
    private val YAML = Spec(
        lineComments = listOf("#"), blockComment = null, yaml = true,
        literals = setOf("true", "false", "null", "yes", "no", "on", "off")
    )
    private val MARKUP = Spec(lineComments = emptyList(), blockComment = null, markup = true)
    private val GENERIC = Spec(
        keywords = words("if else for while do return break continue function class import export from new try catch finally throw switch case default"),
        literals = setOf("true", "false", "null", "nil", "None")
    )

    private fun specFor(language: String): Spec = when (language.trim().lowercase()) {
        "kotlin", "kt", "kts" -> KOTLIN
        "java" -> JAVA
        "javascript", "js", "jsx", "mjs", "typescript", "ts", "tsx" -> JS
        "python", "py" -> PYTHON
        "bash", "sh", "shell", "zsh", "console", "terminal" -> BASH
        "c", "cpp", "c++", "cc", "h", "hpp", "cs", "csharp", "objc" -> C
        "go", "golang" -> GO
        "rust", "rs" -> RUST
        "swift" -> SWIFT
        "sql", "mysql", "postgres", "postgresql", "sqlite" -> SQL
        "css", "scss", "less" -> CSS
        "json", "jsonc", "json5" -> JSON
        "yaml", "yml", "toml", "ini" -> YAML
        "xml", "html", "svg", "xaml", "vue" -> MARKUP
        else -> GENERIC
    }

    private fun buildRules(spec: Spec): List<Rule> {
        if (spec.markup) {
            return listOf(
                Rule(TokenKind.COMMENT, "<!--[\\s\\S]*?(?:-->|\\z)"),
                Rule(TokenKind.KEYWORD, "</?[A-Za-z][\\w:.-]*|/?>"),
                Rule(TokenKind.STRING, "\"[^\"]*\"?|'[^']*'?"),
                Rule(TokenKind.FUNCTION, "[A-Za-z_:][\\w:.-]*(?=\\s*=)")
            )
        }
        val rules = mutableListOf<Rule>()
        val comments = buildList {
            spec.lineComments.forEach { add(Regex.escape(it) + "[^\\n]*") }
            spec.blockComment?.let { (a, b) -> add(Regex.escape(a) + "[\\s\\S]*?(?:" + Regex.escape(b) + "|\\z)") }
        }
        if (comments.isNotEmpty()) rules += Rule(TokenKind.COMMENT, comments.joinToString("|"))

        val strings = mutableListOf<String>()
        if (spec.tripleQuotes) {
            strings += "\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)"
            strings += "'''[\\s\\S]*?(?:'''|\\z)"
        }
        strings += "\"(?:\\\\.|[^\"\\\\\\n])*\"?"
        strings += "'(?:\\\\.|[^'\\\\\\n])*'?"
        if (spec.backticks) strings += "`(?:\\\\.|[^`\\\\])*`?"
        rules += Rule(TokenKind.STRING, strings.joinToString("|"))

        if (spec.yaml) rules += Rule(TokenKind.KEY, "[A-Za-z_][\\w.-]*(?=\\s*:(?:\\s|\\z))")
        if (spec.annotations) rules += Rule(TokenKind.ANNOTATION, "@[A-Za-z_][\\w.]*")
        if (spec.shellVars) rules += Rule(TokenKind.FUNCTION, "\\$\\{?[A-Za-z_]\\w*\\}?|\\$[0-9#?@*!$]")
        rules += Rule(TokenKind.NUMBER, "\\b0[xX][0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFlLuU]*\\b")
        rules += Rule(null, "[A-Za-z_\$][A-Za-z0-9_\$]*")
        return rules
    }

    private fun buildRegex(spec: Spec): Regex =
        Regex(spec.rules.joinToString("|") { "(${it.pattern})" })

    private fun classifyIdentifier(word: String, code: String, end: Int, spec: Spec): TokenKind? {
        if (word in spec.keywords) return TokenKind.KEYWORD
        if (word in spec.literals) return TokenKind.LITERAL
        var i = end
        while (i < code.length && (code[i] == ' ' || code[i] == '\t')) i++
        if (i < code.length && code[i] == '(') return TokenKind.FUNCTION
        val constant = word.length > 1 && word.any { it.isLetter() } && word.all { it.isUpperCase() || it.isDigit() || it == '_' }
        if (constant) return TokenKind.LITERAL
        if (word.length > 1 && word[0].isUpperCase()) return TokenKind.TYPE
        return null
    }

    fun tokenize(code: String, language: String): List<SyntaxToken> {
        if (code.isEmpty() || code.length > MAX_HIGHLIGHT_CHARS) return emptyList()
        val spec = specFor(language)
        val out = ArrayList<SyntaxToken>()
        for (m in spec.regex.findAll(code)) {
            if (m.value.isEmpty()) continue
            val groupIndex = (1..spec.rules.size).first { m.groups[it] != null }
            val rule = spec.rules[groupIndex - 1]
            var kind = rule.kind ?: classifyIdentifier(m.value, code, m.range.last + 1, spec)
            if (kind == TokenKind.STRING && spec.json) {
                // En JSON, un string seguido de ':' es una clave
                var i = m.range.last + 1
                while (i < code.length && code[i].isWhitespace()) i++
                if (i < code.length && code[i] == ':') kind = TokenKind.KEY
            }
            if (kind != null) out += SyntaxToken(m.range.first, m.range.last + 1, kind)
        }
        return out
    }

    private fun styleOf(kind: TokenKind): SpanStyle = when (kind) {
        TokenKind.COMMENT -> SpanStyle(color = SynComment, fontStyle = FontStyle.Italic)
        TokenKind.STRING -> SpanStyle(color = SynString)
        TokenKind.KEY -> SpanStyle(color = SynType)
        TokenKind.NUMBER, TokenKind.LITERAL -> SpanStyle(color = SynNumber)
        TokenKind.KEYWORD -> SpanStyle(color = SynKeyword)
        TokenKind.TYPE -> SpanStyle(color = SynType)
        TokenKind.ANNOTATION -> SpanStyle(color = SynAnnotation)
        TokenKind.FUNCTION -> SpanStyle(color = SynFunction)
    }

    fun highlight(code: String, language: String): AnnotatedString = buildAnnotatedString {
        append(code)
        tokenize(code, language).forEach { addStyle(styleOf(it.kind), it.start, it.end) }
    }
}
