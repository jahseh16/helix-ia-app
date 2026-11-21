package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AgentPhase
import com.example.ui.ChatViewModel
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomInputBar(
    viewModel: ChatViewModel
) {
    var textState by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val agentProgress by viewModel.agentProgress.collectAsStateWithLifecycle()

    val isExecuting = agentProgress.phase != AgentPhase.IDLE &&
                      agentProgress.phase != AgentPhase.COMPLETED &&
                      agentProgress.phase != AgentPhase.FAILED &&
                      agentProgress.phase != AgentPhase.CANCELLED

    val action = {
        if (isExecuting) {
            viewModel.cancelAgentExecution()
        } else if (textState.trim().isNotEmpty()) {
            viewModel.sendMessage(textState)
            textState = ""
            keyboardController?.hide()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(AppSurface.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    brush = if (isExecuting) {
                        Brush.horizontalGradient(listOf(AppDanger, AppDanger.copy(alpha = 0.4f)))
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                AppAccent.copy(alpha = 0.55f),
                                AppCyan.copy(alpha = 0.25f)
                            )
                        )
                    },
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = {
                    Text(
                        text = if (isExecuting) "Helix está trabajando..." else "Pregúntame cualquier cosa…",
                        style = BodyStyle.copy(color = AppTextMuted)
                    )
                },
                enabled = !isExecuting,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { action() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppText,
                    unfocusedTextColor = AppText,
                    disabledTextColor = AppTextMuted,
                    cursorColor = AppAccentSoft,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_textfield"),
                textStyle = BodyStyle
            )

            Spacer(modifier = Modifier.width(8.dp))

            val buttonBrush = if (isExecuting) {
                Brush.linearGradient(listOf(AppDanger, Color(0xFFE57373)))
            } else {
                Brush.linearGradient(listOf(AppAccent, AppCyan))
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(buttonBrush)
                    .clickable { action() }
                    .testTag(if (isExecuting) "stop_button" else "send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExecuting) Icons.Default.Close else Icons.Default.ArrowUpward,
                    contentDescription = if (isExecuting) "Stop execution" else "Send transmission",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
