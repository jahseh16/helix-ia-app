package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.workspace.AccessStatus
import com.example.ui.chat.StorageAccessDialog
import com.example.ui.chat.StorageUnsupportedDialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.runtime.AgentPhase
import com.example.ui.chat.ComposerMenuSheet
import com.example.ui.chat.ApprovalCard
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
import com.example.ui.theme.AppWarning
import com.example.ui.theme.MetaStyle
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.ShapeCard
import com.example.ui.theme.ShapeSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelixApp(viewModel: ChatViewModel) {
    val messages by viewModel.allMessages.collectAsStateWithLifecycle()
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val agentEnabled by viewModel.agentEnabled.collectAsStateWithLifecycle()
    val toolPermissions by viewModel.toolPermissions.collectAsStateWithLifecycle()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()

    val optKey by viewModel.apiKeyOpenRouter.collectAsStateWithLifecycle()
    val groqKey by viewModel.apiKeyGroq.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()

    val agentMode by viewModel.agentMode.collectAsStateWithLifecycle()
    val workspaceInfo by viewModel.workspaceInfo.collectAsStateWithLifecycle()
    val storagePrompt by viewModel.storagePrompt.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showStorageOnboarding.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Al volver de los ajustes de Android (o de cualquier otra pantalla) se recalcula el estado del permiso
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshWorkspace() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android ≤ 9: permisos clásicos de almacenamiento
    val legacyPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshWorkspace()
    }

    /** Abre los ajustes de Android para conceder el acceso (API oficial según la versión). */
    val grantAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val (primary, fallback) = viewModel.allFilesAccessIntents()
            runCatching { context.startActivity(primary) }.onFailure { runCatching { context.startActivity(fallback) } }
        } else {
            legacyPermissions.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
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
                    ChatHistoryList(messages = messages, agentState = agentState)
                }

                agentState.pendingApproval?.let { request ->
                    ApprovalCard(request = request, onDecision = viewModel::respondToApproval)
                }

                agentState.notice?.let { notice ->
                    Text(
                        text = notice,
                        style = MetaStyle.copy(color = AppWarning),
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                    )
                }

                BottomInputBar(
                    isGenerating = agentState.isBusy,
                    statusText = agentStatusText(agentState),
                    agentEnabled = agentEnabled,
                    agentMode = agentMode,
                    onModeChange = viewModel::setAgentMode,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::cancelGeneration,
                    onOpenMenu = { showMenu = true }
                )
            }
        }
    }

    if (showOnboarding || storagePrompt) {
        if (workspaceInfo.status == AccessStatus.INTERNAL_ONLY) {
            StorageUnsupportedDialog(onDismiss = {
                viewModel.dismissStorageOnboarding()
                viewModel.dismissStoragePrompt()
            })
        } else {
            StorageAccessDialog(
                onAllow = {
                    viewModel.dismissStorageOnboarding()
                    viewModel.dismissStoragePrompt()
                    grantAccess()
                },
                onDeny = {
                    viewModel.dismissStorageOnboarding()
                    viewModel.dismissStoragePrompt()
                }
            )
        }
    }

    if (showMenu) {
        ComposerMenuSheet(
            provider = selectedProvider,
            modelId = selectedModelId,
            agentEnabled = agentEnabled,
            onSelectModel = viewModel::selectModel,
            onToggleAgent = { viewModel.setAgentEnabled(!agentEnabled) },
            onDismiss = { showMenu = false }
        )
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
                permissions = toolPermissions,
                onPermissionChange = viewModel::setToolPermission,
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

/** Texto de estado que se muestra dentro de la píldora mientras el agente trabaja. */
private fun agentStatusText(state: AgentUiState): String? {
    if (!state.isBusy) return null
    val what = when (state.phase) {
        AgentPhase.AWAITING_APPROVAL -> "Esperando tu aprobación"
        AgentPhase.RUNNING_TOOL -> "Ejecutando herramienta"
        else -> "Pensando"
    }
    return if (state.step > 0) "Paso ${state.step}/${state.maxSteps} · $what…" else "$what…"
}
