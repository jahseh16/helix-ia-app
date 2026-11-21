package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HighlightedCodeText(code: String, language: String) {
    val annotatedString = remember(code, language) {
        val lang = language.lowercase()
        buildAnnotatedString {
            append(code)

            val commentRegexes = when (lang) {
                "bash", "shell", "sh" -> listOf("""#.*""".toRegex())
                "html", "xml" -> listOf("""<!--.*?-->""".toRegex(RegexOption.DOT_MATCHES_ALL))
                "css" -> listOf("""/\*.*?\*/""".toRegex(RegexOption.DOT_MATCHES_ALL))
                else -> listOf(
                    """//.*""".toRegex(),
                    """/\*.*?\*/""".toRegex(RegexOption.DOT_MATCHES_ALL)
                )
            }

            val stringRegexes = listOf(
                """"[^"\\]*(?:\\.[^"\\]*)*"""".toRegex(),
                """'[^'\\]*(?:\\.[^'\\]*)*'""".toRegex()
            )

            val keywords = when (lang) {
                "js", "javascript", "ts", "typescript" -> listOf(
                    "const", "let", "var", "function", "return", "if", "else", "for", "while",
                    "import", "export", "class", "from", "await", "async", "try", "catch", "new"
                )
                "json" -> listOf("true", "false", "null")
                "bash", "shell", "sh" -> listOf(
                    "sudo", "apt", "npm", "pnpm", "yarn", "pm2", "git", "ssh", "cd", "ls", "node",
                    "mkdir", "rm", "cp", "mv", "chmod", "curl", "wget", "echo", "status", "install"
                )
                "html", "xml" -> listOf(
                    "div", "span", "p", "a", "h1", "h2", "h3", "h4", "h5", "h6", "script", "style", "link",
                    "class", "id", "href", "src", "type", "rel", "meta"
                )
                "css" -> listOf(
                    "color", "background", "border", "padding", "margin", "display", "position",
                    "width", "height", "font", "flex", "align", "justify"
                )
                else -> listOf("return", "if", "else", "for", "while", "class", "import")
            }

            for (regex in stringRegexes) {
                regex.findAll(code).forEach { match ->
                    addStyle(
                        style = SpanStyle(color = AppGreen),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }

            val keywordPattern = if (lang == "html" || lang == "xml") {
                """\b(${keywords.joinToString("|")})\b|<\/?([a-zA-Z0-9]+)""".toRegex()
            } else {
                """\b(${keywords.joinToString("|")})\b""".toRegex()
            }

            keywordPattern.findAll(code).forEach { match ->
                addStyle(
                    style = SpanStyle(color = AppCyan, fontWeight = FontWeight.Bold),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            """\b(\d+|true|false|null)\b""".toRegex().findAll(code).forEach { match ->
                addStyle(
                    style = SpanStyle(color = AppPink),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }

            for (regex in commentRegexes) {
                regex.findAll(code).forEach { match ->
                    addStyle(
                        style = SpanStyle(color = AppTextMuted),
                        start = match.range.first,
                        end = match.range.last + 1
                    )
                }
            }
        }
    }

    Text(
        text = annotatedString,
        color = Color(0xFFE7EBF3),
        fontSize = 12.sp,
        lineHeight = 19.sp,
        fontFamily = UiMono,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun CodeBlockContainer(
    code: String,
    language: String,
    onCopy: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0C111A),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, AppCyan.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppSurface2)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase().ifEmpty { "CODE" },
                    style = MetaStyle.copy(
                        color = AppCyan,
                        fontFamily = UiMono,
                        fontWeight = FontWeight.Bold
                    )
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (copied) AppGreen.copy(alpha = 0.14f)
                            else AppAccent.copy(alpha = 0.14f)
                        )
                        .border(
                            1.dp,
                            if (copied) AppGreen.copy(alpha = 0.40f) else AppAccent.copy(alpha = 0.40f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            onCopy()
                            copied = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) AppGreen else AppAccentSoft,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (copied) "Copiado" else "Copiar",
                        style = MetaStyle.copy(
                            color = if (copied) AppGreen else AppText,
                            fontFamily = UiSans,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                HighlightedCodeText(code = code, language = language)
            }
        }
    }
}
