package com.example.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Contenido de un mensaje: Markdown y bloques de código resaltados, en el orden en que aparecen. */
@Composable
fun MessageContentWithCodeBlocks(text: String, isUser: Boolean) {
    val parts = remember(text) { parseMessageText(text) }

    Column(
        // La burbuja del usuario necesita relleno interno; la respuesta de la IA va libre sobre el fondo
        modifier = if (isUser) Modifier.padding(horizontal = 14.dp, vertical = 12.dp) else Modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        parts.forEach { part ->
            when (part) {
                is MessagePart.TextBlock -> MarkdownText(text = part.text, isUser = isUser)
                is MessagePart.CodeBlock ->
                    if (part.language.lowercase() == "files") FilesBlockContainer(code = part.code)
                    else CodeBlockContainer(code = part.code, language = part.language)
            }
        }
    }
}

sealed class MessagePart {
    data class TextBlock(val text: String) : MessagePart()
    data class CodeBlock(val code: String, val language: String) : MessagePart()
}

/**
 * Separa el texto en párrafos Markdown y bloques ```code```. Un bloque sin cerrar (típico a mitad de
 * un streaming) se trata como código hasta el final, para que no parpadee entre texto y código.
 */
fun parseMessageText(text: String): List<MessagePart> {
    val parts = mutableListOf<MessagePart>()
    val prose = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var language = ""

    fun flushProse() {
        val t = prose.toString().trim('\n')
        if (t.isNotBlank()) parts += MessagePart.TextBlock(t)
        prose.clear()
    }

    for (line in text.split("\n")) {
        val trimmed = line.trim()
        if (!inCode) {
            if (trimmed.startsWith("```")) {
                flushProse()
                inCode = true
                language = trimmed.removePrefix("```").trim().substringBefore(' ')
                code.clear()
            } else {
                prose.append(line).append('\n')
            }
        } else if (trimmed == "```") {
            parts += MessagePart.CodeBlock(code.toString().trimEnd('\n'), language)
            inCode = false
        } else {
            code.append(line).append('\n')
        }
    }
    if (inCode) parts += MessagePart.CodeBlock(code.toString().trimEnd('\n'), language)
    flushProse()
    return parts
}
