package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.runtime.HistoryMapper
import com.example.agent.tools.ImageResult
import com.example.data.database.ChatMessageEntity
import com.example.ui.AgentUiState
import com.example.ui.LiveToolState
import com.example.ui.theme.AppAccent
import com.example.ui.theme.AppCyan
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.BodyStyle
import com.example.ui.theme.TitleStyle

@Composable
fun ChatHistoryList(messages: List<ChatMessageEntity>, agentState: AgentUiState, userName: String = "") {
    val listState = rememberLazyListState()

    // Los resultados de herramientas se pintan dentro de la tarjeta de su llamada, no como filas sueltas
    val visible = remember(messages) { messages.filter { it.sender != ChatMessageEntity.SENDER_TOOL } }
    val toolResults = remember(messages) {
        messages.filter { it.sender == ChatMessageEntity.SENDER_TOOL && it.toolCallId != null }.associateBy { it.toolCallId!! }
    }

    val hasLiveReasoning = agentState.streamingReasoning.isNotBlank()
    val hasLiveText = agentState.streamingText.isNotBlank()
    val showThinking = agentState.isBusy && !hasLiveText && !hasLiveReasoning &&
        agentState.liveTools.isEmpty() && agentState.pendingApproval == null &&
        agentState.phase == com.example.agent.runtime.AgentPhase.STREAMING
    val liveItems = listOf(hasLiveReasoning, hasLiveText, showThinking).count { it }
    val itemCount = visible.size + liveItems

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }
    // Mientras llega texto en streaming se mantiene el final a la vista (sin animar cada fragmento)
    LaunchedEffect(agentState.streamingText.length / 40, agentState.liveTools.size) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    if (itemCount == 0) {
        EmptyState(userName)
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(items = visible, key = { it.id }) { message ->
            when (message.sender) {
                ChatMessageEntity.SENDER_USER -> UserBubble(message.text)
                ChatMessageEntity.SENDER_AI -> AiTurn(message, toolResults, agentState)
                ChatMessageEntity.SENDER_ERROR -> ErrorNote(message.text)
                else -> SystemNote(message.text)
            }
        }
        if (hasLiveReasoning) {
            item(key = "live-reasoning") {
                ReasoningBlock(text = agentState.streamingReasoning, live = true, key = "live-reasoning")
            }
        }
        if (hasLiveText) {
            item(key = "live-text") {
                Column {
                    ModelLabel("Respondiendo")
                    AiMessageText(agentState.streamingText + " ▍", showCopy = false)
                }
            }
        }
        if (showThinking) {
            item(key = "thinking") { ThinkingBubble() }
        }
    }
}

/** Un turno del asistente: razonamiento plegable, texto y una tarjeta por cada herramienta que pidió. */
@Composable
private fun AiTurn(
    message: ChatMessageEntity,
    toolResults: Map<String, ChatMessageEntity>,
    agentState: AgentUiState
) {
    val calls = remember(message.toolCallsJson) { HistoryMapper.parseToolCalls(message.toolCallsJson) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.modelUsed != null) ModelLabel(message.modelUsed) else ModelLabel("Asistente")
        if (!message.reasoning.isNullOrBlank()) {
            ReasoningBlock(text = message.reasoning, live = false, key = "reasoning-${message.id}")
        }
        if (message.text.isNotBlank()) {
            AiMessageText(message.text, showCopy = true, messageId = message.id)
        }
        calls.forEach { call ->
            ToolCard(
                call = call,
                result = toolResults[call.id],
                live = agentState.liveTools[call.id],
                agentBusy = agentState.isBusy,
                modifier = Modifier.padding(end = 8.dp)
            )
            val imagePath = if (call.name == ImageResult.TOOL_NAME && toolResults[call.id]?.toolStatus == "OK")
                toolResults[call.id]?.text?.let { ImageResult.pathFrom(it) } else null
            if (imagePath != null) GeneratedImage(path = imagePath, modifier = Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
private fun EmptyState(userName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            com.example.ui.components.HelixLogo(size = 84.dp, corner = 26.dp)

            val first = userName.trim().substringBefore(' ')
            Text(text = if (first.isNotEmpty()) "Hola, $first. ¿En qué te ayudo?" else "¿En qué te ayudo hoy?", style = TitleStyle.copy(fontSize = 24.sp), textAlign = TextAlign.Center)

            Text(
                text = "Busco en la web, abro tus apps, genero imágenes y trabajo con tus archivos. " +
                    "Configura tu API key en Ajustes (menú ☰ → ⚙) para empezar.",
                style = BodyStyle.copy(color = AppTextMuted, fontSize = 15.sp, lineHeight = 22.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.86f)
            )
        }
    }
}
