package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/** Copia [textToCopy] al portapapeles y muestra "Copiado" con un check durante 2 segundos. */
@Composable
fun CopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    label: String = "Copiar",
    pill: Boolean = false,
    description: String = "Copiar al portapapeles"
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val tint = if (copied) AppGreen else AppTextMuted
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .then(if (pill) Modifier.background(if (copied) AppGreen.copy(alpha = 0.14f) else AppSurface3.copy(alpha = 0.7f)) else Modifier)
            .clickable {
                clipboard.setText(AnnotatedString(textToCopy))
                copied = true
            }
            .padding(horizontal = if (pill) 12.dp else 8.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (copied) "Copiado" else description
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (copied) "Copiado" else label,
            style = MetaStyle.copy(color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}
