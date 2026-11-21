package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*

fun buildMarkdownAnnotatedString(text: String, defaultColor: Color, isUser: Boolean = false): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val nextBold = text.indexOf("**", index)
            val nextCode = text.indexOf("`", index)

            if (nextBold != -1 && (nextCode == -1 || nextBold < nextCode)) {
                append(text.substring(index, nextBold))

                val endBold = text.indexOf("**", nextBold + 2)
                if (endBold != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (isUser) AppText else AppCyan))
                    append(text.substring(nextBold + 2, endBold))
                    pop()
                    index = endBold + 2
                } else {
                    append("**")
                    index = nextBold + 2
                }
            } else if (nextCode != -1 && (nextBold == -1 || nextCode < nextBold)) {
                append(text.substring(index, nextCode))

                val endCode = text.indexOf("`", nextCode + 1)
                if (endCode != -1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = UiMono,
                            color = AppAccentSoft,
                            background = Color.White.copy(alpha = 0.08f)
                        )
                    )
                    append(text.substring(nextCode + 1, endCode))
                    pop()
                    index = endCode + 1
                } else {
                    append("`")
                    index = nextCode + 1
                }
            } else {
                append(text.substring(index))
                break
            }
        }
    }
}
