package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.chat.BottomInputBar
import com.example.ui.chat.ChatHistoryList
import com.example.ui.components.ErrorBanner
import com.example.ui.components.HelixTopBar
import com.example.ui.settings.SettingsPanel
import com.example.ui.theme.AppAccent
import com.example.ui.theme.AppBg
import com.example.ui.theme.AppDanger
import com.example.ui.theme.AppStroke
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppSurface2
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.ShapeCard
import com.example.ui.theme.ShapeSheet

private val OpenRouterModels = listOf(
    "cohere/north-mini-code:free" to "cohere/north-mini-code:free",
    "nvidia/nemotron-3.5-content-safety:free" to "nvidia/nemotron-3.5-content-safety:free",
    "nvidia/nemotron-3-ultra-550b-a55b:free" to "nvidia/nemotron-3-ultra-550b-a55b:free",
    "poolside/laguna-m.1:free" to "poolside/laguna-m.1:free",
    "openai/gpt-oss-20b:free" to "openai/gpt-oss-20b:free",
    "poolside/laguna-xs-2.1:free" to "poolside/laguna-xs-2.1:free"
)

private val GroqModels = listOf(
    "llama-4-scout-mock" to "Llama 4 Scout",
    "llama-3.3-70b-versatile" to "Llama 3.3 70B",
    "qwen-2.5-32b" to "Qwen 3 32B / Qwen 3.6 27B",
    "gpt-oss-20b" to "GPT OSS 120B / GPT OSS 20B",
    "whisper-large-v3" to "Whisper Large v3 / Turbo"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelixApp(viewModel: ChatViewModel) {
    val messages by viewModel.allMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()

    val optKey by viewModel.apiKeyOpenRouter.collectAsStateWithLifecycle()
    val groqKey by viewModel.apiKeyGroq.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBg)
                // Resplandor azul sutil en la parte superior
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(AppAccent.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 1.1f
                        )
                    )
                }
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HelixTopBar(
                    provider = selectedProvider,
                    modelId = selectedModelId,
                    onOpenSettings = { showSettings = true },
                    onClearChat = { confirmClear = true }
                )

                if (storageError.isNotBlank()) {
                    ErrorBanner(text = storageError)
                }

                Box(modifier = Modifier.weight(1f)) {
                    ChatHistoryList(messages = messages, isGenerating = isGenerating)
                }

                BottomInputBar(
                    isGenerating = isGenerating,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::cancelGeneration
                )
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppSurface,
            shape = ShapeSheet,
            dragHandle = { BottomSheetDefaults.DragHandle(color = AppStroke) }
        ) {
            SettingsPanel(
                optKey = optKey,
                groqKey = groqKey,
                selectedProvider = selectedProvider,
                selectedModelId = selectedModelId,
                systemPrompt = systemPrompt,
                openRouterModels = OpenRouterModels,
                groqModels = GroqModels,
                onSave = { oKey, gKey, provider, model, prompt ->
                    viewModel.updateSettings(oKey, gKey, provider, model, prompt)
                    showSettings = false
                }
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = AppSurface2,
            shape = ShapeCard,
            title = { Text("¿Limpiar la conversación?", color = AppText, fontWeight = FontWeight.SemiBold) },
            text = { Text("Se borrará todo el historial de este chat. No se puede deshacer.", color = AppTextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text("Limpiar", color = AppDanger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = AppAccent) }
            }
        )
    }
}
