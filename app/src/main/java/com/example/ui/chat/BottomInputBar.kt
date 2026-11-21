package com.example.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppAccent
import com.example.ui.theme.AppDanger
import com.example.ui.theme.AppStroke
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppSurface3
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.BodyStyle

@Composable
fun BottomInputBar(
    isGenerating: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit
) {
    var textState by remember { mutableStateOf("") }
    val canSend = textState.isNotBlank()

    val send = {
        if (canSend) {
            onSend(textState)
            textState = ""
        }
    }

    val buttonColor by animateColorAsState(
        targetValue = when {
            isGenerating -> AppDanger
            canSend -> AppAccent
            else -> AppSurface3
        },
        label = "send_button_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .background(AppSurface)
            .border(1.dp, AppStroke, androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            placeholder = {
                Text(
                    text = if (isGenerating) "Helix está respondiendo…" else "Escribe un mensaje…",
                    style = BodyStyle.copy(color = AppTextMuted)
                )
            },
            enabled = !isGenerating,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { send() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppText,
                unfocusedTextColor = AppText,
                disabledTextColor = AppTextMuted,
                cursorColor = AppAccent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("chat_input_textfield"),
            textStyle = BodyStyle
        )

        Box(
            modifier = Modifier
                .padding(bottom = 4.dp, end = 2.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(enabled = isGenerating || canSend) { if (isGenerating) onStop() else send() }
                .testTag(if (isGenerating) "stop_button" else "send_button")
                .semantics {
                    role = Role.Button
                    contentDescription = if (isGenerating) "Detener respuesta" else "Enviar mensaje"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = if (isGenerating || canSend) Color.White else AppTextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
