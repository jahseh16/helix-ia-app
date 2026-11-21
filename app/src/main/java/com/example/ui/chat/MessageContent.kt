package com.example.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*

@Composable
fun MessageContentWithCodeBlocks(
    text: String,
    isUser: Boolean,
    isTermuxOut: Boolean,
    isTermuxErr: Boolean,
    isSystem: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    val parts = remember(text) { parseMessageText(text) }

    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        parts.forEach { part ->
            when (part) {
                is MessagePart.TextBlock -> {
                    val isLogOrSystem = isTermuxOut || isTermuxErr || isSystem
                    if (isLogOrSystem) {
                        Text(
                            text = part.text.trim(),
                            style = BodyStyle.copy(
                                fontFamily = UiMono,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = when {
                                    isTermuxOut -> AppGreen
                                    isTermuxErr -> AppDanger
                                    else -> AppTextMuted
                                }
                            )
                        )
                    } else {
                        val defaultColor = if (isUser) AppText else AppText.copy(alpha = 0.97f)
                        val annotatedText = remember(part.text) {
                            buildMarkdownAnnotatedString(part.text.trim(), defaultColor, isUser)
                        }
                        Text(
                            text = annotatedText,
                            style = BodyStyle.copy(
                                color = defaultColor,
                                lineHeight = 21.sp
                            )
                        )
                    }
                }

                is MessagePart.CodeBlock -> {
                    if (part.language.lowercase() == "files") {
                        FilesBlockContainer(code = part.code)
                    } else {
                        CodeBlockContainer(
                            code = part.code,
                            language = part.language,
                            onCopy = {
                                val clip = android.content.ClipData.newPlainText("Copied Code", part.code)
                                clipboardManager.setPrimaryClip(clip)
                            }
                        )
                    }
                }
            }
        }
    }
}

sealed class MessagePart {
    data class TextBlock(val text: String) : MessagePart()
    data class CodeBlock(val code: String, val language: String) : MessagePart()
}

fun parseMessageText(text: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val regex = """```([a-zA-Z0-9+#-]*)\n?(.*?)\n?```""".toRegex(RegexOption.DOT_MATCHES_ALL)
    var lastIndex = 0

    regex.findAll(text).forEach { matchResult ->
        val textBefore = text.substring(lastIndex, matchResult.range.first)
        if (textBefore.isNotEmpty()) {
            parts.add(MessagePart.TextBlock(textBefore))
        }
        val language = matchResult.groupValues[1]
        val code = matchResult.groupValues[2]
        parts.add(MessagePart.CodeBlock(code, language))
        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < text.length) {
        val textAfter = text.substring(lastIndex)
        if (textAfter.isNotEmpty()) {
            parts.add(MessagePart.TextBlock(textAfter))
        }
    }

    if (parts.isEmpty() && text.isNotEmpty()) {
        parts.add(MessagePart.TextBlock(text))
    }

    return parts
}
