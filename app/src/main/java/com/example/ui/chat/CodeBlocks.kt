package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Bloque de código: contenedor oscuro redondeado, barra superior con el lenguaje y "Copiar código",
 * resaltado de sintaxis y scroll horizontal (las líneas largas no se cortan ni se parten).
 */
@Composable
fun CodeBlockContainer(code: String, language: String, modifier: Modifier = Modifier) {
    val highlighted = remember(code, language) { SyntaxHighlighter.highlight(code, language) }
    val scroll = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("code_block"),
        color = AppCodeBg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppStroke)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppSurface2)
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.trim().lowercase().ifEmpty { "código" },
                    style = MetaStyle.copy(color = AppAccentSoft, fontFamily = UiMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                CopyButton(textToCopy = code, label = "Copiar código", description = "Copiar código")
            }

            Box(modifier = Modifier.horizontalScroll(scroll)) {
                Text(
                    text = highlighted,
                    color = AppText,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    fontFamily = UiMono,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}
