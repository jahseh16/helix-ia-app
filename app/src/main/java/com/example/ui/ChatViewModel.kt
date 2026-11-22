package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.llm.OpenAiCompatClient
import com.example.agent.model.LlmMessage
import com.example.agent.permissions.AgentMode
import com.example.agent.permissions.Permission
import com.example.agent.permissions.PermissionPolicy
import com.example.agent.runtime.AgentEvent
import com.example.agent.runtime.AgentPhase
import com.example.agent.runtime.AgentPrompt
import com.example.agent.runtime.AgentRunner
import com.example.agent.runtime.ApprovalDecision
import com.example.agent.runtime.ApprovalGate
import com.example.agent.runtime.HistoryMapper
import com.example.agent.tools.EditFileTool
import com.example.agent.tools.GlobTool
import com.example.agent.tools.GrepTool
import com.example.agent.tools.HttpRequestTool
import com.example.agent.tools.ListDirTool
import com.example.agent.tools.ReadFileTool
import com.example.agent.tools.ShellTool
import com.example.agent.tools.ToolRegistry
import com.example.agent.tools.WebFetchTool
import com.example.agent.tools.Workspace
import com.example.agent.tools.GenerateImageTool
import com.example.agent.tools.WebSearchTool
import com.example.agent.device.AndroidDeviceBridge
import com.example.agent.device.DeviceInfoTool
import com.example.agent.device.ListAppsTool
import com.example.agent.device.OpenAppTool
import com.example.agent.device.OpenUrlTool
import com.example.data.security.Secrets
import com.example.tts.FishAudioClient
import com.example.tts.TtsController
import com.example.tts.TtsVoices
import com.example.tts.resolveFishKey
import com.example.agent.tools.WriteFileTool
import com.example.data.database.AppDatabase
import com.example.data.database.ChatMessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.security.SecureStore
import com.example.data.security.SecureStoreException
import com.example.data.workspace.AccessStatus
import com.example.data.workspace.WorkspaceInfo
import com.example.data.workspace.WorkspaceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import android.net.Uri
import com.example.data.database.ConversationEntity
import com.example.data.profile.ProfileStore
import com.example.data.profile.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

// @JvmOverloads es imprescindible: la fábrica por defecto de ViewModels instancia por
// reflexión el constructor (Application) y sin él la app crashea al abrir.
class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val secureStore: SecureStore = SecureStore.create(application),
    workspaceRoot: File = File(application.filesDir, "workspace")
) : AndroidViewModel(application) {

    private val sharedPrefs =
        application.getSharedPreferences("helix_prefs", Context.MODE_PRIVATE)

    private val repository = ChatRepository(AppDatabase.getDatabase(application).chatMessageDao())

    val workspace = Workspace(workspaceRoot)
    private val workspaceManager = WorkspaceManager(application, sharedPrefs, workspaceRoot)

    // Streaming SSE: sin read timeout global; una petición cancelada se aborta con Call.cancel()
    private val llmHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // Herramientas de red: las redirecciones las gestiona la propia herramienta (re-validando cada salto)
    private val toolsHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val deviceBridge = AndroidDeviceBridge(application)

    private val registry = ToolRegistry(
        listOf(
            ReadFileTool(workspace), ListDirTool(workspace), GlobTool(workspace), GrepTool(workspace),
            WriteFileTool(workspace), EditFileTool(workspace),
            HttpRequestTool(toolsHttp), WebFetchTool(toolsHttp),
            WebSearchTool(toolsHttp),
            ListAppsTool(deviceBridge), OpenAppTool(deviceBridge), OpenUrlTool(deviceBridge), DeviceInfoTool(deviceBridge),
            GenerateImageTool(toolsHttp.newBuilder().readTimeout(90, TimeUnit.SECONDS).build(), { File(application.filesDir, "images") }),
            ShellTool(workspace)
        )
    )

    // --- Conversaciones: 0 = chat nuevo todavía sin guardar (se crea al enviar el primer mensaje)
    val currentConversationId = MutableStateFlow(sharedPrefs.getLong(PREF_CURRENT_CONVERSATION, 0L))

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allMessages: StateFlow<List<ChatMessageEntity>> = currentConversationId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else repository.messages(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        migrateLegacyPreferences()
        // Si la conversación guardada ya no existe (se borró), se empieza un chat nuevo
        viewModelScope.launch {
            val id = currentConversationId.value
            if (!sharedPrefs.contains(PREF_CURRENT_CONVERSATION)) {
                // Primera vez con historial: se abre el chat que ya existía (migrado como conversación 1)
                if (repository.exists(1L)) setCurrentConversation(1L)
            } else if (id != 0L && !repository.exists(id)) setCurrentConversation(0L)
        }
    }

    private fun setCurrentConversation(id: Long) {
        currentConversationId.value = id
        sharedPrefs.edit().putLong(PREF_CURRENT_CONVERSATION, id).apply()
    }

    /** Abre una conversación del historial. Detiene la generación y el audio en curso. */
    fun selectConversation(id: Long) {
        if (id == currentConversationId.value) return
        generationJob?.cancel()
        tts.stop()
        policy.clearSessionGrants()
        setCurrentConversation(id)
    }

    /** Empieza un chat vacío; se guarda en el historial cuando se envía el primer mensaje. */
    fun newChat() {
        generationJob?.cancel()
        tts.stop()
        policy.clearSessionGrants()
        setCurrentConversation(0L)
    }

    fun deleteConversation(id: Long) {
        if (id == currentConversationId.value) newChat()
        viewModelScope.launch { repository.deleteConversation(id) }
    }

    fun deleteAllConversations() {
        newChat()
        viewModelScope.launch { repository.deleteAll() }
    }

    // --- Perfil del usuario (local)
    private val profileStore = ProfileStore(sharedPrefs, File(application.filesDir, "profile"))
    val profile = MutableStateFlow(profileStore.load())

    /** Pantalla de perfil inicial: solo la primera vez (hasta que se guarde u omita). */
    val showProfileSetup = MutableStateFlow(!profileStore.isSetupDone)

    fun saveProfile(name: String, username: String, email: String, newPhoto: Uri?, removePhoto: Boolean = false) {
        viewModelScope.launch {
            val photo = when {
                newPhoto != null -> withContext(Dispatchers.IO) {
                    profileStore.importPhoto(getApplication<Application>().contentResolver, newPhoto)
                } ?: profile.value.photoPath
                removePhoto -> null
                else -> profile.value.photoPath
            }
            val updated = UserProfile(name.trim(), UserProfile.cleanUsername(username), email.trim(), photo)
            profileStore.save(updated)
            profile.value = updated
            showProfileSetup.value = false
        }
    }

    fun skipProfileSetup() {
        profileStore.markSetupDone()
        showProfileSetup.value = false
    }

    val apiKeyOpenRouter = MutableStateFlow(secureStore.getString(PREF_OPENROUTER).orEmpty())
    val apiKeyGroq = MutableStateFlow(secureStore.getString(PREF_GROQ).orEmpty())
    val selectedProvider =
        MutableStateFlow(sharedPrefs.getString(PREF_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER)
    val selectedModelId =
        MutableStateFlow(
            (sharedPrefs.getString(PREF_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
                // Un modelo que se retiró de la lista (ya no responde al chat) vuelve al predeterminado
                .let { if (it in ModelCatalog.retiredIds) DEFAULT_MODEL else it }
        )
    val systemPrompt =
        MutableStateFlow(sharedPrefs.getString(PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT)

    /** Si está desactivado, el modelo responde como chat normal (con memoria y streaming) sin herramientas. */
    val agentEnabled = MutableStateFlow(sharedPrefs.getBoolean(PREF_AGENT_ENABLED, true))

    /** Mensaje si no se pudo guardar un secreto cifrado; vacío si todo va bien. */
    val storageError = MutableStateFlow("")

    val agentState = MutableStateFlow(AgentUiState())

    // --- Voz (Fish Audio): la key del archivo de secretos manda; si está vacía se usa la de Ajustes
    private val secrets = Secrets.load(application)
    // Respaldo invisible: una key guardada por una versión anterior desde Ajustes
    private val fishKeySetting = secureStore.getString(PREF_FISH).orEmpty()
    val ttsVoiceId = MutableStateFlow(TtsVoices.byId(sharedPrefs.getString(PREF_TTS_VOICE, null)).id)

    val tts = TtsController(
        context = application,
        scope = viewModelScope,
        client = FishAudioClient(
            OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
        ),
        keyProvider = { resolveFishKey(secrets.fishAudioApiKey, fishKeySetting) },
        voiceProvider = { ttsVoiceId.value }
    )

    fun setTtsVoice(voiceId: String) {
        ttsVoiceId.value = TtsVoices.byId(voiceId).id
        sharedPrefs.edit().putString(PREF_TTS_VOICE, ttsVoiceId.value).apply()
    }

    override fun onCleared() {
        tts.release()
        super.onCleared()
    }

    private val policy = PermissionPolicy(
        initial = PermissionPolicy.TOOL_NAMES.mapNotNull { tool ->
            sharedPrefs.getString(PREF_PERM_PREFIX + tool, null)
                ?.let { runCatching { Permission.valueOf(it) }.getOrNull() }
                ?.let { tool to it }
        }.toMap(),
        onChange = { levels ->
            sharedPrefs.edit().apply { levels.forEach { (tool, p) -> putString(PREF_PERM_PREFIX + tool, p.name) } }.apply()
        }
    )

    init {
        // Una versión anterior dejaba "Generar imagen" bloqueada: se restablece una sola vez
        if (!sharedPrefs.getBoolean(PREF_PERMS_V3, false)) {
            if (policy.levelOf("generate_image") == Permission.DENY) policy.setLevel("generate_image", Permission.AUTO)
            sharedPrefs.edit().putBoolean(PREF_PERMS_V3, true).apply()
        }
    }

    val toolPermissions = MutableStateFlow(policy.snapshot())

    /** Modo global (Automático / Pedir permiso). Se aplica al instante y se conserva entre sesiones. */
    val agentMode = MutableStateFlow(
        runCatching { AgentMode.valueOf(sharedPrefs.getString(PREF_AGENT_MODE, null) ?: "") }.getOrDefault(AgentMode.ASK)
    ).also { policy.mode = it.value }

    /** Estado del permiso global de almacenamiento. */
    val workspaceInfo = MutableStateFlow(workspaceManager.evaluate())

    /** Una herramienta falló por falta del permiso: la UI vuelve a ofrecerlo. */
    val storagePrompt = MutableStateFlow(false)

    /**
     * Diálogo inicial (solo la primera vez): pide el permiso global, o avisa de la carpeta interna en Android 10.
     * No aparece si el permiso ya está concedido.
     */
    val showStorageOnboarding = MutableStateFlow(
        !sharedPrefs.getBoolean(WorkspaceManager.KEY_ONBOARDING_DONE, false) &&
            workspaceInfo.value.status != AccessStatus.GRANTED
    )

    init {
        workspaceManager.cleanupLegacySaf()
        applyWorkspace(workspaceInfo.value)
    }

    private fun applyWorkspace(info: WorkspaceInfo) {
        workspace.changeRoot(info.root, info.aliases)
        // Sin el permiso global, archivos y shell se bloquean y avisan al modelo en vez de fallar en silencio
        workspace.blockedReason = if (info.needsAction) info.message else null
    }

    private var generationJob: Job? = null
    private var pendingDecision: CompletableDeferred<ApprovalDecision>? = null

    private val approvalGate = ApprovalGate { request ->
        val deferred = CompletableDeferred<ApprovalDecision>()
        pendingDecision = deferred
        agentState.update { it.copy(pendingApproval = request) }
        try {
            deferred.await()
        } finally {
            pendingDecision = null
            agentState.update { it.copy(pendingApproval = null) }
        }
    }

    /**
     * - Versiones anteriores guardaban las API keys en texto plano en las preferencias:
     *   se cifran una vez y se borra la copia en claro (solo si el cifrado tuvo éxito).
     * - Después se purga cualquier clave de preferencias que la app ya no use.
     */
    private fun migrateLegacyPreferences() {
        listOf(PREF_OPENROUTER, PREF_GROQ).forEach { name ->
            val legacy = sharedPrefs.getString(name, null) ?: return@forEach
            try {
                if (legacy.isNotEmpty() && secureStore.getString(name).isNullOrEmpty()) {
                    secureStore.putString(name, legacy)
                }
                sharedPrefs.edit().remove(name).apply()
            } catch (e: SecureStoreException) {
                // Si no se puede cifrar, se conserva la copia legada para no perder la key.
            }
        }

        val stale = sharedPrefs.all.keys
            .filter { it !in KNOWN_PREFS && !it.startsWith(PREF_PERM_PREFIX) } - setOf(PREF_OPENROUTER, PREF_GROQ)
        if (stale.isNotEmpty()) {
            sharedPrefs.edit().apply { stale.forEach { remove(it) } }.apply()
        }
    }

    fun updateSettings(
        openRouterKey: String,
        groqKey: String,
        provider: String,
        modelId: String,
        sysPrompt: String
    ) {
        apiKeyOpenRouter.value = openRouterKey
        apiKeyGroq.value = groqKey
        systemPrompt.value = sysPrompt

        try {
            secureStore.putString(PREF_OPENROUTER, openRouterKey)
            secureStore.putString(PREF_GROQ, groqKey)
            storageError.value = ""
        } catch (e: SecureStoreException) {
            // La key queda en memoria para esta sesión, pero NO se guarda en claro.
            storageError.value = "No se pudieron guardar las API keys de forma segura. " +
                "Tendrás que introducirlas de nuevo al reiniciar la app."
        }

        sharedPrefs.edit().putString(PREF_SYSTEM_PROMPT, sysPrompt).apply()
        selectModel(provider, modelId)
    }

    /** Cambio rápido de proveedor/modelo desde el chip junto al campo de texto. */
    fun selectModel(provider: String, modelId: String) {
        selectedProvider.value = provider
        selectedModelId.value = modelId
        sharedPrefs.edit().putString(PREF_PROVIDER, provider).putString(PREF_MODEL, modelId).apply()
    }

    fun setAgentEnabled(enabled: Boolean) {
        agentEnabled.value = enabled
        sharedPrefs.edit().putBoolean(PREF_AGENT_ENABLED, enabled).apply()
    }

    fun setToolPermission(tool: String, level: Permission) {
        policy.setLevel(tool, level)
        toolPermissions.value = policy.snapshot()
    }

    fun setAgentMode(mode: AgentMode) {
        policy.mode = mode
        agentMode.value = mode
        sharedPrefs.edit().putString(PREF_AGENT_MODE, mode.name).apply()
    }

    /** Recalcula el permiso (al volver de los ajustes de Android o al reanudar la app). */
    fun refreshWorkspace() {
        val info = workspaceManager.evaluate()
        applyWorkspace(info)
        workspaceInfo.value = info
        if (!info.needsAction) storagePrompt.value = false
    }

    fun dismissStorageOnboarding() {
        sharedPrefs.edit().putBoolean(WorkspaceManager.KEY_ONBOARDING_DONE, true).apply()
        showStorageOnboarding.value = false
    }

    fun dismissStoragePrompt() {
        storagePrompt.value = false
    }

    fun allFilesAccessIntents() =
        listOf(workspaceManager.allFilesAccessSettingsIntent(), workspaceManager.allFilesAccessFallbackIntent())

    val supportsDirectAccess: Boolean get() = workspaceManager.supportsDirectAccess()

    fun respondToApproval(decision: ApprovalDecision) {
        pendingDecision?.complete(decision)
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || agentState.value.isBusy) return

        val provider = selectedProvider.value
        val model = selectedModelId.value
        val apiKey = if (provider == "OpenRouter") apiKeyOpenRouter.value else apiKeyGroq.value
        val toolsEnabled = agentEnabled.value
        val modelLabel = "$provider · $model"

        agentState.value = AgentUiState(phase = AgentPhase.STREAMING, maxSteps = AgentRunner.DEFAULT_MAX_STEPS)
        generationJob = viewModelScope.launch {
            // La conversación se fija al empezar: si el usuario cambia de chat, lo que llegue se guarda en la suya
            var conv = currentConversationId.value
            try {
                if (conv == 0L || !repository.exists(conv)) {
                    conv = repository.createConversation(trimmed)
                    setCurrentConversation(conv)
                }
                repository.insertMessage(conv, ChatMessageEntity(sender = ChatMessageEntity.SENDER_USER, text = trimmed))

                // Historial COMPLETO (ya incluye el mensaje recién guardado), no solo el último turno
                val history = HistoryMapper.toLlmMessages(repository.snapshot(conv))
                val messages = listOf<LlmMessage>(
                    LlmMessage.System(AgentPrompt.build(systemPrompt.value, toolsEnabled))
                ) + history

                val runner = AgentRunner(
                    llm = OpenAiCompatClient.forProvider(provider, apiKey, llmHttp),
                    tools = registry,
                    policy = policy,
                    approvals = approvalGate,
                    model = model
                )
                runner.run(messages, toolsEnabled).collect { handle(it, modelLabel, conv) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    if (conv != 0L && repository.exists(conv)) repository.insertMessage(
                        conv,
                        ChatMessageEntity(sender = ChatMessageEntity.SENDER_SYSTEM, text = "Detenido por el usuario.")
                    )
                }
                throw e
            } catch (e: Exception) {
                if (conv != 0L) repository.insertMessage(
                    conv,
                    ChatMessageEntity(
                        sender = ChatMessageEntity.SENDER_ERROR,
                        text = "No fue posible completar la solicitud.\n${e.message ?: "Error desconocido"}"
                    )
                )
            } finally {
                agentState.value = AgentUiState()
                generationJob = null
            }
        }
    }

    private suspend fun handle(event: AgentEvent, modelLabel: String, conv: Long) {
        when (event) {
            is AgentEvent.Phase -> agentState.update { it.copy(phase = event.phase, step = event.step) }
            is AgentEvent.Text -> agentState.update { it.copy(streamingText = it.streamingText + event.delta) }
            is AgentEvent.Reasoning -> agentState.update { it.copy(streamingReasoning = it.streamingReasoning + event.delta) }
            is AgentEvent.ToolCallPreview -> Unit

            is AgentEvent.AssistantTurn -> {
                val m = event.message
                repository.insertMessage(
                    conv,
                    ChatMessageEntity(
                        sender = ChatMessageEntity.SENDER_AI,
                        text = m.content,
                        modelUsed = modelLabel,
                        toolCallsJson = HistoryMapper.toolCallsToJson(m.toolCalls),
                        reasoning = m.reasoning.ifBlank { null }
                    )
                )
                agentState.update { it.copy(streamingText = "", streamingReasoning = "") }
            }

            is AgentEvent.AwaitingApproval ->
                agentState.update { it.copy(liveTools = it.liveTools + (event.call.id to LiveToolState.AWAITING_APPROVAL)) }

            is AgentEvent.ToolStarted ->
                agentState.update { it.copy(liveTools = it.liveTools + (event.call.id to LiveToolState.RUNNING)) }

            is AgentEvent.ToolFinished -> {
                repository.insertMessage(
                    conv,
                    ChatMessageEntity(
                        sender = ChatMessageEntity.SENDER_TOOL,
                        text = event.result.output,
                        toolCallId = event.call.id,
                        toolName = event.call.name,
                        toolArgs = event.call.argumentsJson,
                        toolStatus = event.result.status.name
                    )
                )
                if (event.result.needsStorageAccess) storagePrompt.value = true
                agentState.update { it.copy(liveTools = it.liveTools - event.call.id) }
            }

            is AgentEvent.Notice -> agentState.update { it.copy(notice = event.text) }

            is AgentEvent.Done -> when (event.phase) {
                AgentPhase.FAILED -> repository.insertMessage(
                    conv,
                    ChatMessageEntity(sender = ChatMessageEntity.SENDER_ERROR, text = event.message ?: "Error desconocido")
                )
                AgentPhase.STEP_LIMIT -> repository.insertMessage(
                    conv,
                    ChatMessageEntity(sender = ChatMessageEntity.SENDER_SYSTEM, text = event.message ?: "Límite de pasos alcanzado")
                )
                else -> Unit
            }
        }
    }

    /** Detiene la ejecución en curso: aborta la petición HTTP, la espera de aprobación y la herramienta activa. */
    fun cancelGeneration() {
        generationJob?.cancel()
    }

    /** Borra la conversación abierta del historial y empieza un chat nuevo. */
    fun clearHistory() {
        val id = currentConversationId.value
        if (id != 0L) deleteConversation(id) else newChat()
    }

    companion object {
        private const val PREF_OPENROUTER = "api_key_openrouter"
        private const val PREF_GROQ = "api_key_groq"
        private const val PREF_PROVIDER = "selected_provider"
        private const val PREF_MODEL = "selected_model_id"
        private const val PREF_AGENT_ENABLED = "agent_enabled"
        private const val PREF_AGENT_MODE = "agent_mode"
        private const val PREF_FISH = "fish_audio_key"
        private const val PREF_PERMS_V3 = "perms_migrated_v3"
        private const val PREF_CURRENT_CONVERSATION = "current_conversation_id"
        private const val PREF_TTS_VOICE = "tts_voice_id"
        private const val PREF_PERM_PREFIX = "perm_"
        // Clave nueva a propósito: descarta el prompt por defecto anterior guardado en el dispositivo.
        private const val PREF_SYSTEM_PROMPT = "system_prompt_v2"

        private val KNOWN_PREFS = setOf(
            PREF_PROVIDER, PREF_MODEL, PREF_SYSTEM_PROMPT, PREF_AGENT_ENABLED, PREF_AGENT_MODE, PREF_TTS_VOICE, PREF_PERMS_V3, PREF_CURRENT_CONVERSATION,
            WorkspaceManager.KEY_ONBOARDING_DONE
        ) + ProfileStore.KEYS

        private const val DEFAULT_PROVIDER = "OpenRouter"
        private const val DEFAULT_MODEL = ModelCatalog.DEFAULT_OPENROUTER
        private const val DEFAULT_SYSTEM_PROMPT =
            "Eres Helix AI, un asistente técnico de programación preciso y directo."
    }
}
