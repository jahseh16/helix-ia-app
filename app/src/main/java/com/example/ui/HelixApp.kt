package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ChatViewModel
import com.example.ui.ConnectionState
import com.example.ui.chat.*
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelixApp(viewModel: ChatViewModel) {
    val messages by viewModel.allMessages.collectAsStateWithLifecycle()
    val wsState by viewModel.webSocketState.collectAsStateWithLifecycle()
    val wsError by viewModel.webSocketErrorMsg.collectAsStateWithLifecycle()

    val optKey by viewModel.apiKeyOpenRouter.collectAsStateWithLifecycle()
    val groqKey by viewModel.apiKeyGroq.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val termuxUrl by viewModel.termuxUrl.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    val openRouterModels = listOf(
        "cohere/north-mini-code:free" to "cohere/north-mini-code:free",
        "nvidia/nemotron-3.5-content-safety:free" to "nvidia/nemotron-3.5-content-safety:free",
        "nvidia/nemotron-3-ultra-550b-a55b:free" to "nvidia/nemotron-3-ultra-550b-a55b:free",
        "poolside/laguna-m.1:free" to "poolside/laguna-m.1:free",
        "openai/gpt-oss-20b:free" to "openai/gpt-oss-20b:free",
        "poolside/laguna-xs-2.1:free" to "poolside/laguna-xs-2.1:free"
    )

    val groqModels = listOf(
        "llama-4-scout-mock" to "Llama 4 Scout",
        "llama-3.3-70b-versatile" to "Llama 3.3 70B",
        "qwen-2.5-32b" to "Qwen 3 32B / Qwen 3.6 27B",
        "gpt-oss-20b" to "GPT OSS 120B / GPT OSS 20B",
        "whisper-large-v3" to "Whisper Large v3 / Turbo"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HelixTopBar(
                wsState = wsState,
                showSettings = showSettings,
                onSettingsToggle = { showSettings = !showSettings },
                onReconnect = { viewModel.connectWebSocket() },
                onClearChat = { viewModel.clearHistory() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkBackground,
                            TitaniumGray,
                            GlacierCyan.copy(alpha = 0.15f)
                        ),
                        center = Offset.Unspecified,
                        radius = 1200f
                    )
                )
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (showSettings) 14.dp else 0.dp)
            ) {
                if (wsState == ConnectionState.ERROR && wsError.isNotBlank()) {
                    ErrorBanner(text = "Termux desconectado: $wsError")
                }

                if (storageError.isNotBlank()) {
                    ErrorBanner(text = storageError)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    ChatHistoryList(messages = messages)
                }

                AgentProgressCard(viewModel = viewModel)

                BottomInputBar(
                    viewModel = viewModel
                )
            }

            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showSettings = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // Prevent click propagation inside the modal card from closing it
                            }
                    ) {
                        SettingsPanel(
                            optKey = optKey,
                            groqKey = groqKey,
                            selectedProvider = selectedProvider,
                            selectedModelId = selectedModelId,
                            termuxUrl = termuxUrl,
                            systemPrompt = systemPrompt,
                            openRouterModels = openRouterModels,
                            groqModels = groqModels,
                            onClose = { showSettings = false },
                            onSave = { oKey, gKey, prov, model, url, prompt ->
                                viewModel.updateSettings(oKey, gKey, prov, model, url, prompt)
                                showSettings = false
                            }
                        )
                    }
                }
            }
        }
    }
}
