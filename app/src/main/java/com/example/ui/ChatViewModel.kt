package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ChatMessageEntity
import com.example.data.network.LlmService
import com.example.data.repository.ChatRepository
import com.example.data.security.SecureStore
import com.example.data.security.SecureStoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// @JvmOverloads es imprescindible: la fábrica por defecto de ViewModels instancia por
// reflexión el constructor (Application) y sin él la app crashea al abrir.
class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val secureStore: SecureStore = SecureStore.create(application)
) : AndroidViewModel(application) {

    private val sharedPrefs =
        application.getSharedPreferences("helix_prefs", Context.MODE_PRIVATE)

    private val repository = ChatRepository(AppDatabase.getDatabase(application).chatMessageDao())
    private val llmService = LlmService()

    val allMessages: StateFlow<List<ChatMessageEntity>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        migrateLegacyPreferences()
    }

    val apiKeyOpenRouter = MutableStateFlow(secureStore.getString(PREF_OPENROUTER).orEmpty())
    val apiKeyGroq = MutableStateFlow(secureStore.getString(PREF_GROQ).orEmpty())
    val selectedProvider =
        MutableStateFlow(sharedPrefs.getString(PREF_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER)
    val selectedModelId =
        MutableStateFlow(sharedPrefs.getString(PREF_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val systemPrompt =
        MutableStateFlow(sharedPrefs.getString(PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT)

    /** Mensaje si no se pudo guardar un secreto cifrado; vacío si todo va bien. */
    val storageError = MutableStateFlow("")

    /** `true` mientras se espera la respuesta del modelo. */
    val isGenerating = MutableStateFlow(false)

    private var generationJob: Job? = null

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

        val stale = sharedPrefs.all.keys - KNOWN_PREFS - setOf(PREF_OPENROUTER, PREF_GROQ)
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
        selectedProvider.value = provider
        selectedModelId.value = modelId
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

        sharedPrefs.edit().apply {
            putString(PREF_PROVIDER, provider)
            putString(PREF_MODEL, modelId)
            putString(PREF_SYSTEM_PROMPT, sysPrompt)
            apply()
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isGenerating.value) return

        val provider = selectedProvider.value
        val model = selectedModelId.value
        val apiKey = if (provider == "OpenRouter") apiKeyOpenRouter.value else apiKeyGroq.value

        isGenerating.value = true
        generationJob = viewModelScope.launch {
            try {
                repository.insertMessage(ChatMessageEntity(sender = SENDER_USER, text = trimmed))

                val result = llmService.getCompletion(
                    provider = provider,
                    apiKey = apiKey,
                    modelId = model,
                    prompt = trimmed,
                    systemPrompt = systemPrompt.value
                )

                val reply = result.getOrNull()
                val messageText = reply ?: buildString {
                    append("No fue posible completar la solicitud.\n")
                    append(result.exceptionOrNull()?.message ?: "Error desconocido")
                    append("\n\nRevisa tu API key en la configuración.")
                }

                repository.insertMessage(
                    ChatMessageEntity(
                        sender = SENDER_AI,
                        text = messageText,
                        modelUsed = "$provider · $model"
                    )
                )
            } finally {
                isGenerating.value = false
                generationJob = null
            }
        }
    }

    /** Detiene la petición en curso (cancela también la llamada HTTP). */
    fun cancelGeneration() {
        generationJob?.cancel()
    }

    fun clearHistory() {
        generationJob?.cancel()
        viewModelScope.launch {
            repository.clearMessages()
            repository.insertMessage(
                ChatMessageEntity(sender = SENDER_SYSTEM, text = "La conversación se limpió correctamente.")
            )
        }
    }

    companion object {
        private const val SENDER_USER = "USER"
        private const val SENDER_AI = "AI"
        private const val SENDER_SYSTEM = "SYSTEM"

        private const val PREF_OPENROUTER = "api_key_openrouter"
        private const val PREF_GROQ = "api_key_groq"
        private const val PREF_PROVIDER = "selected_provider"
        private const val PREF_MODEL = "selected_model_id"
        // Clave nueva a propósito: descarta el prompt por defecto anterior guardado en el dispositivo.
        private const val PREF_SYSTEM_PROMPT = "system_prompt_v2"

        private val KNOWN_PREFS = setOf(PREF_PROVIDER, PREF_MODEL, PREF_SYSTEM_PROMPT)

        private const val DEFAULT_PROVIDER = "OpenRouter"
        private const val DEFAULT_MODEL = "cohere/north-mini-code:free"
        private const val DEFAULT_SYSTEM_PROMPT =
            "Eres Helix AI, un asistente técnico de programación preciso y directo."
    }
}
