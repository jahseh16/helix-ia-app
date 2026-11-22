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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.sp
import com.example.ui.components.StarfieldBackground
import com.example.ui.drawer.ChatDrawer
import com.example.ui.profile.ProfileForm
import com.example.ui.profile.ProfileSetupScreen
import com.example.ui.theme.TitleStyle
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.ui.chat.LocalTts
import com.example.ui.chat.RepoSuggestState
import com.example.ui.connectors.ConnectorsSheet
import androidx.compose.ui.platform.testTag
import com.example.ui.chat.TtsUi
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

    val ttsState by viewModel.tts.state.collectAsStateWithLifecycle()
    val ttsVoice by viewModel.ttsVoiceId.collectAsStateWithLifecycle()
    val ttsUi = remember(ttsState) { TtsUi(ttsState, viewModel.tts::toggle, viewModel.tts::stop) }

    val agentMode by viewModel.agentMode.collectAsStateWithLifecycle()
    val workspaceInfo by viewModel.workspaceInfo.collectAsStateWithLifecycle()
    val storagePrompt by viewModel.storagePrompt.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showStorageOnboarding.collectAsStateWithLifecycle()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConversation by viewModel.currentConversationId.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val showProfileSetup by viewModel.showProfileSetup.collectAsStateWithLifecycle()

    // Primera apertura: perfil de usuario antes que nada
    if (showProfileSetup) {
        ProfileSetupScreen(
            onSave = { n, u, e, photo -> viewModel.saveProfile(n, u, e, photo) },
            onSkip = viewModel::skipProfileSetup
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Al volver de los ajustes de Android (o de cualquier otra pantalla) se recalcula el estado del permiso
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { viewModel.refreshWorkspace(); viewModel.refreshBackgroundAccess() }
                Lifecycle.Event.ON_START -> viewModel.onAppForeground(true)
                Lifecycle.Event.ON_STOP -> viewModel.onAppForeground(false)
                else -> Unit
            }
        }
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

    val backgroundUnrestricted by viewModel.backgroundUnrestricted.collectAsStateWithLifecycle()
    val requestBackground: () -> Unit = {
        val (primary, fallback) = viewModel.backgroundAccessIntents()
        runCatching { context.startActivity(primary) }.onFailure { runCatching { context.startActivity(fallback) } }
    }

    // Android 13+: permiso de notificaciones para avisar al terminar de conectar en el navegador
    var pendingConnector by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingConnector?.let(viewModel::connectConnector)
        pendingConnector = null
    }
    val connectWithPermission: (String) -> Unit = { id ->
        if (Build.VERSION.SDK_INT >= 33 && !com.example.notifications.ConnectNotifier.canNotify(context)) {
            pendingConnector = id
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.connectConnector(id)
    }

    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showConnectors by remember { mutableStateOf(false) }

    val githubState by viewModel.githubConnector.state.collectAsStateWithLifecycle()
    val githubRepos by viewModel.githubRepos.collectAsStateWithLifecycle()
    val githubLoading by viewModel.githubReposLoading.collectAsStateWithLifecycle()
    val connectorPrompt by viewModel.connectorPrompt.collectAsStateWithLifecycle()
    val githubLogin = (githubState as? com.example.connectors.ConnectorState.Connected)?.account?.login

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeDrawerThen: (() -> Unit) -> Unit = { action -> scope.launch { drawerState.close() }; action() }

    // Efecto cristal: lo que queda detrás de un panel abierto se desenfoca (Android 12+)
    val overlayOpen = drawerState.targetValue == DrawerValue.Open || showMenu || showSettings || showProfileEdit || showConnectors
    val blur by animateDpAsState(if (overlayOpen) 14.dp else 0.dp, label = "glass-blur")

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AppSurface,
                drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
                modifier = Modifier.width(310.dp).border(1.dp, AppStroke, RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp))
            ) {
                ChatDrawer(
                    conversations = conversations,
                    currentId = currentConversation,
                    profile = profile,
                    onNewChat = { closeDrawerThen(viewModel::newChat) },
                    onSelect = { id -> closeDrawerThen { viewModel.selectConversation(id) } },
                    onDelete = viewModel::deleteConversation,
                    onDeleteAll = viewModel::deleteAllConversations,
                    onOpenProfile = { closeDrawerThen { showProfileEdit = true } },
                    onOpenSettings = { closeDrawerThen { showSettings = true } },
                    onOpenConnectors = { closeDrawerThen { showConnectors = true } },
                    githubLogin = githubLogin
                )
            }
        }
    ) {
    StarfieldBackground(modifier = Modifier.blur(blur)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HelixTopBar(
                    provider = selectedProvider,
                    modelId = selectedModelId,
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onNewChat = viewModel::newChat
                )

                if (storageError.isNotBlank()) {
                    ErrorBanner(text = storageError)
                }

                Box(modifier = Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalTts provides ttsUi) {
                        ChatHistoryList(messages = messages, agentState = agentState, userName = profile.name)
                    }
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
                    onOpenMenu = { showMenu = true },
                    repoSuggest = RepoSuggestState(
                        connected = githubLogin != null,
                        repos = githubRepos,
                        loading = githubLoading,
                        onNeedRepos = { viewModel.loadGithubRepos() },
                        onOpenConnectors = { showConnectors = true }
                    )
                )
            }
        }
    }
    }

    if (showConnectors) {
        ConnectorsSheet(
            connectors = viewModel.connectors.all,
            onConnect = connectWithPermission,
            onCancel = viewModel::cancelConnector,
            onDisconnect = viewModel::disconnectConnector,
            onDismiss = { showConnectors = false },
            backgroundUnrestricted = backgroundUnrestricted,
            onRequestBackground = requestBackground
        )
    }

    // Una herramienta necesitaba un conector que no está conectado: acceso directo a Conectores
    connectorPrompt?.let { id ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConnectorPrompt,
            containerColor = AppSurface2,
            shape = ShapeCard,
            title = { Text("${if (id == "github") "GitHub" else "El conector"} no está conectado", color = AppText, fontWeight = FontWeight.SemiBold) },
            text = { Text("Para trabajar con tus repositorios, conecta tu cuenta en Conectores y vuelve a pedírselo al agente.", color = AppTextMuted) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissConnectorPrompt(); showConnectors = true }, modifier = Modifier.testTag("connector_prompt_open")) {
                    Text("Abrir Conectores", color = AppAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissConnectorPrompt) { Text("Ahora no", color = AppTextMuted) } }
        )
    }

    if (showProfileEdit) {
        ModalBottomSheet(
            onDismissRequest = { showProfileEdit = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppSurface,
            shape = ShapeSheet,
            dragHandle = { BottomSheetDefaults.DragHandle(color = AppStroke) }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Text("Tu perfil", style = TitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 16.dp))
                ProfileForm(initial = profile, primaryLabel = "Guardar") { n, u, e, photo, remove ->
                    viewModel.saveProfile(n, u, e, photo, remove)
                    showProfileEdit = false
                }
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
            onOpenConnectors = { showMenu = false; showConnectors = true },
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
                ttsVoiceId = ttsVoice,
                onSaveTts = viewModel::setTtsVoice,
                onPermissionChange = viewModel::setToolPermission,
                onSave = { oKey, gKey, provider, model, prompt ->
                    viewModel.updateSettings(oKey, gKey, provider, model, prompt)
                    showSettings = false
                }
            )
        }
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
