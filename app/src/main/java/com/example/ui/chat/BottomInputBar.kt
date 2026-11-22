package com.example.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.agent.permissions.AgentMode
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
import com.example.ui.theme.*

private val PillShape = RoundedCornerShape(30.dp)

/**
 * Una sola píldora: botón "+" (modelo, agente, workspace) a la izquierda, campo de texto en el centro y
 * enviar/detener a la derecha. El estado del agente se muestra en el propio placeholder.
 */
@Composable
fun BottomInputBar(
    isGenerating: Boolean,
    statusText: String?,
    agentEnabled: Boolean,
    agentMode: AgentMode,
    onModeChange: (AgentMode) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenMenu: () -> Unit,
    repoSuggest: RepoSuggestState = RepoSuggestState()
) {
    // rememberSaveable: el borrador sobrevive a rotaciones y a cambios de modo/modelo
    var textState by rememberSaveable { mutableStateOf("") }
    val canSend = textState.isNotBlank()

    // @ o / al escribir: sugiere repositorios de GitHub (o avisa de que no está conectado)
    val trigger = if (isGenerating) null else Mentions.detect(textState)
    LaunchedEffect(trigger != null, repoSuggest.connected) {
        if (trigger != null && repoSuggest.connected) repoSuggest.onNeedRepos()
    }

    val send = {
        if (canSend) {
            onSend(textState)
            textState = ""
        }
    }

    val sendColor by animateColorAsState(
        targetValue = when {
            isGenerating -> AppDanger
            canSend -> AppAccent
            else -> AppSurface3
        },
        label = "send_button_color"
    )

    Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
    if (trigger != null) {
        RepoSuggestionPanel(
            trigger = trigger,
            state = repoSuggest,
            onPick = { repo -> textState = Mentions.insert(textState, trigger, repo.fullName) }
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(PillShape)
            .background(AppSurface)
            .border(1.dp, AppStroke, PillShape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // "+" con un punto azul si el modo agente está activo
        Box(modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppSurface2)
                    .clickable(onClick = onOpenMenu)
                    .testTag("plus_button")
                    .semantics {
                        role = Role.Button
                        contentDescription = "Opciones: modelo, agente y workspace"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = AppAccentSoft, modifier = Modifier.size(24.dp))
            }
            if (agentEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AppAccent)
                        .border(2.dp, AppSurface, CircleShape)
                )
            }
        }

        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            placeholder = {
                Text(
                    text = statusText ?: "Escribe un mensaje…",
                    style = BodyStyle.copy(color = if (statusText != null) AppAccentSoft else AppTextMuted),
                    maxLines = 1
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
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("chat_input_textfield"),
            textStyle = BodyStyle
        )

        if (agentEnabled) {
            AgentModeSelector(
                mode = agentMode,
                onModeChange = onModeChange,
                modifier = Modifier.padding(bottom = 6.dp, end = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .padding(bottom = 2.dp, end = 2.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(sendColor)
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
}
