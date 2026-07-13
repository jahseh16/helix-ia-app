package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ChatMessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.network.LlmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class AgentPhase {
    IDLE,
    PLANNING,
    VALIDATING_PATH,
    EXECUTING,
    RETRYING,
    PARSING_OUTPUT,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AgentProgress(
    val phase: AgentPhase = AgentPhase.IDLE,
    val message: String = "",
    val currentAttempt: Int = 1,
    val maxAttempts: Int = 4,
    val currentCommand: String = ""
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs =
        application.getSharedPreferences("helix_prefs", Context.MODE_PRIVATE)

    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.chatMessageDao())
    private val llmService = LlmService()

    val allMessages: StateFlow<List<ChatMessageEntity>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val apiKeyOpenRouter =
        MutableStateFlow(sharedPrefs.getString("api_key_openrouter", "") ?: "")
    val apiKeyGroq =
        MutableStateFlow(sharedPrefs.getString("api_key_groq", "") ?: "")
    val selectedProvider =
        MutableStateFlow(sharedPrefs.getString("selected_provider", "OpenRouter") ?: "OpenRouter")
    val selectedModelId =
        MutableStateFlow(
            sharedPrefs.getString(
                "selected_model_id",
                "cohere/north-mini-code:free"
            ) ?: "cohere/north-mini-code:free"
        )
    val termuxUrl =
        MutableStateFlow(sharedPrefs.getString("termux_url", "ws://127.0.0.1:8080") ?: "ws://127.0.0.1:8080")
    val systemPrompt =
        MutableStateFlow(
            sharedPrefs.getString(
                "system_prompt",
                "You are Helix AI, a precise coding assistant connected to the user's local Termux environment."
            ) ?: ""
        )

    val webSocketState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val webSocketErrorMsg = MutableStateFlow("")

    val agentProgress = MutableStateFlow(AgentProgress())

    private var activeWebSocket: WebSocket? = null
    private var agentJob: Job? = null

    private var activeCommandDeferred: CompletableDeferred<CommandResult>? = null
    private var accumulatedStdout = StringBuilder()
    private var accumulatedStderr = StringBuilder()

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var lastStreamedMessageId: Long? = null

    companion object {
        private const val SENDER_USER = "USER"
        private const val SENDER_AI = "AI"
        private const val SENDER_TERMUX_OUT = "TERMUX_OUT"
        private const val SENDER_TERMUX_ERR = "TERMUX_ERR"
        private const val SENDER_TERMUX_SYS = "TERMUX_SYS"

        private const val PREF_OPENROUTER = "api_key_openrouter"
        private const val PREF_GROQ = "api_key_groq"
        private const val PREF_PROVIDER = "selected_provider"
        private const val PREF_MODEL = "selected_model_id"
        private const val PREF_TERMUX_URL = "termux_url"
        private const val PREF_SYSTEM_PROMPT = "system_prompt"

        private const val DEFAULT_PROVIDER = "OpenRouter"
        private const val DEFAULT_MODEL = "cohere/north-mini-code:free"
        private const val DEFAULT_TERMUX_URL = "ws://127.0.0.1:8080"

        private const val THINKING_TEXT = "Pensando…"
    }

    init {
        connectWebSocket()
    }

    fun updateSettings(
        openRouterKey: String,
        groqKey: String,
        provider: String,
        modelId: String,
        termuxAddr: String,
        sysPrompt: String
    ) {
        apiKeyOpenRouter.value = openRouterKey
        apiKeyGroq.value = groqKey
        selectedProvider.value = provider
        selectedModelId.value = modelId
        systemPrompt.value = sysPrompt

        val urlChanged = termuxUrl.value != termuxAddr
        termuxUrl.value = termuxAddr

        sharedPrefs.edit().apply {
            putString(PREF_OPENROUTER, openRouterKey)
            putString(PREF_GROQ, groqKey)
            putString(PREF_PROVIDER, provider)
            putString(PREF_MODEL, modelId)
            putString(PREF_TERMUX_URL, termuxAddr)
            putString(PREF_SYSTEM_PROMPT, sysPrompt)
            apply()
        }

        if (urlChanged) {
            disconnectWebSocket()
            connectWebSocket()
        }
    }

    fun connectWebSocket() {
        if (webSocketState.value == ConnectionState.CONNECTED ||
            webSocketState.value == ConnectionState.CONNECTING
        ) {
            return
        }

        webSocketState.value = ConnectionState.CONNECTING
        webSocketErrorMsg.value = ""

        val request = try {
            Request.Builder().url(termuxUrl.value).build()
        } catch (e: Exception) {
            webSocketState.value = ConnectionState.ERROR
            webSocketErrorMsg.value = "URL no válida: ${e.localizedMessage}"
            return
        }

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocketState.value = ConnectionState.CONNECTED
                lastStreamedMessageId = null

                viewModelScope.launch {
                    insertSystemMessage(
                        "Conexión con Termux establecida correctamente en ${termuxUrl.value}."
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                viewModelScope.launch {
                    handleWebSocketMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                webSocketState.value = ConnectionState.DISCONNECTED
                lastStreamedMessageId = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                webSocketState.value = ConnectionState.ERROR
                webSocketErrorMsg.value = t.localizedMessage ?: "No fue posible conectar"
                lastStreamedMessageId = null

                viewModelScope.launch {
                    insertSystemMessage(
                        "No fue posible conectar con el servidor de Termux. Verifica que esté activo y accesible."
                    )
                }
            }
        })
    }

    fun disconnectWebSocket() {
        activeWebSocket?.close(1000, "User disconnected")
        activeWebSocket = null
        webSocketState.value = ConnectionState.DISCONNECTED
        lastStreamedMessageId = null
    }

    private suspend fun handleWebSocketMessage(jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val type = json.optString("type")
            val data = json.optString("data")

            when (type) {
                "status" -> {
                    insertSystemMessage(data)
                }

                "stdout" -> {
                    accumulatedStdout.append(data)
                    appendOrInsertMessage(SENDER_TERMUX_OUT, data)
                }

                "stderr" -> {
                    accumulatedStderr.append(data)
                    appendOrInsertMessage(SENDER_TERMUX_ERR, data)
                }

                "exit" -> {
                    val exitMsg = data.trim()
                    val exitCode = if (exitMsg.contains("code 0")) 0 else {
                        """\b(\d+)\b""".toRegex().find(exitMsg)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    }
                    val formattedExit = when {
                        exitMsg.contains("code 0") ->
                            "\nEjecución completada correctamente.\n"

                        exitMsg.isNotEmpty() ->
                            "\nLa ejecución finalizó con error: $exitMsg\n"

                        else ->
                            "\nProceso finalizado.\n"
                    }

                    appendOrInsertMessage(SENDER_TERMUX_SYS, formattedExit)
                    activeCommandDeferred?.complete(
                        CommandResult(
                            exitCode = exitCode,
                            stdout = accumulatedStdout.toString(),
                            stderr = accumulatedStderr.toString()
                        )
                    )
                    lastStreamedMessageId = null
                }

                "error" -> {
                    appendOrInsertMessage(SENDER_TERMUX_ERR, "Error: $data")
                    activeCommandDeferred?.complete(
                        CommandResult(
                            exitCode = 1,
                            stdout = accumulatedStdout.toString(),
                            stderr = accumulatedStderr.toString() + "\nError: $data"
                        )
                    )
                    lastStreamedMessageId = null
                }
            }
        } catch (e: Exception) {
            appendOrInsertMessage(SENDER_TERMUX_OUT, jsonText)
        }
    }

    private suspend fun appendOrInsertMessage(sender: String, text: String) {
        val lastId = lastStreamedMessageId

        if (lastId != null) {
            val existingMessage = withContext(Dispatchers.IO) {
                database.chatMessageDao().getMessageById(lastId)
            }

            if (existingMessage != null && existingMessage.sender == sender) {
                val updatedMessage = existingMessage.copy(
                    text = existingMessage.text + text,
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    database.chatMessageDao().insertMessage(updatedMessage)
                }
                return
            }
        }

        val newMessage = ChatMessageEntity(
            sender = sender,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        val newId = withContext(Dispatchers.IO) {
            database.chatMessageDao().insertMessage(newMessage)
        }

        if (sender == SENDER_TERMUX_OUT || sender == SENDER_TERMUX_ERR) {
            lastStreamedMessageId = newId
        }
    }

    private suspend fun insertSystemMessage(text: String) {
        val msg = ChatMessageEntity(
            sender = SENDER_TERMUX_SYS,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        withContext(Dispatchers.IO) {
            database.chatMessageDao().insertMessage(msg)
        }
    }

    fun cancelAgentExecution() {
        agentJob?.cancel()
        agentJob = null
        activeCommandDeferred?.complete(
            CommandResult(
                exitCode = -1,
                stdout = accumulatedStdout.toString(),
                stderr = accumulatedStderr.toString() + "\n[Ejecución cancelada por el usuario]"
            )
        )
        agentProgress.value = AgentProgress(
            phase = AgentPhase.CANCELLED,
            message = "Proceso cancelado por el usuario"
        )
        viewModelScope.launch {
            insertSystemMessage("Ejecución cancelada por el usuario.")
        }
    }

    private suspend fun executeAndAwaitCommand(command: String): CommandResult {
        accumulatedStdout.clear()
        accumulatedStderr.clear()
        val deferred = CompletableDeferred<CommandResult>()
        activeCommandDeferred = deferred

        executeTermuxCommand(command)

        return try {
            withTimeout(30000) {
                deferred.await()
            }
        } catch (e: Exception) {
            CommandResult(
                exitCode = -99,
                stdout = accumulatedStdout.toString(),
                stderr = accumulatedStderr.toString() + "\n[Timeout o interrupción de ejecución]"
            )
        } finally {
            activeCommandDeferred = null
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        agentJob?.cancel()

        agentJob = viewModelScope.launch {
            try {
                val userMsg = ChatMessageEntity(
                    sender = SENDER_USER,
                    text = trimmed,
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    database.chatMessageDao().insertMessage(userMsg)
                }

                val isExplicitCmd =
                    trimmed.startsWith("/cmd ") || trimmed.startsWith("/termux ")

                if (isExplicitCmd) {
                    val command = trimmed
                        .substringAfter("/cmd ", trimmed)
                        .substringAfter("/termux ", trimmed)
                        .trim()

                    agentProgress.value = AgentProgress(
                        phase = AgentPhase.EXECUTING,
                        message = "Ejecutando comando directo...",
                        currentCommand = command
                    )
                    executeAndAwaitCommand(command)
                    agentProgress.value = AgentProgress(
                        phase = AgentPhase.COMPLETED,
                        message = "Comando directo completado"
                    )
                    return@launch
                }

                var currentPrompt = trimmed
                var attempt = 1
                val maxAttempts = 4
                var keepGoing = true

                agentProgress.value = AgentProgress(
                    phase = AgentPhase.PLANNING,
                    message = "Analizando instrucción...",
                    currentAttempt = attempt,
                    maxAttempts = maxAttempts
                )

                while (keepGoing && attempt <= maxAttempts) {
                    val activeProvider = selectedProvider.value
                    val activeModel = selectedModelId.value
                    val activeKey =
                        if (activeProvider == "OpenRouter") apiKeyOpenRouter.value else apiKeyGroq.value

                    agentProgress.value = AgentProgress(
                        phase = if (attempt == 1) AgentPhase.PLANNING else AgentPhase.RETRYING,
                        message = if (attempt == 1) "Analizando instrucción..." else "Probando alternativa $attempt de $maxAttempts...",
                        currentAttempt = attempt,
                        maxAttempts = maxAttempts
                    )

                    val thinkingMsg = ChatMessageEntity(
                        sender = SENDER_AI,
                        text = "Pensando…",
                        timestamp = System.currentTimeMillis() + 10,
                        modelUsed = "$activeProvider - $activeModel"
                    )

                    val thinkingId = withContext(Dispatchers.IO) {
                        database.chatMessageDao().insertMessage(thinkingMsg)
                    }

                    val result = llmService.getCompletion(
                        provider = activeProvider,
                        apiKey = activeKey,
                        modelId = activeModel,
                        prompt = currentPrompt,
                        systemPrompt = systemPrompt.value
                    )

                    var proposedCommand: String? = null

                    result.onSuccess { reply ->
                        val executePattern =
                            """\[(?:EXECUTE|EJECUTAR):\s*(.+?)\s*]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                        val matchResult = executePattern.find(reply)
                        proposedCommand = matchResult?.groupValues?.get(1)?.trim()

                        val cleanReply = if (proposedCommand != null) {
                            reply.replace(executePattern, "").trim()
                        } else {
                            reply
                        }

                        withContext(Dispatchers.IO) {
                            val finalMsg = thinkingMsg.copy(
                                id = thinkingId,
                                text = cleanReply,
                                timestamp = System.currentTimeMillis()
                            )
                            database.chatMessageDao().insertMessage(finalMsg)
                        }
                    }.onFailure { err ->
                        proposedCommand = null
                        withContext(Dispatchers.IO) {
                            val errorDetail = buildString {
                                append("No fue posible completar la solicitud.\n")
                                append(err.message ?: "Error desconocido")
                                append("\n\nRevisa tu API key en la configuración.")
                            }

                            val errorMsg = thinkingMsg.copy(
                                id = thinkingId,
                                text = errorDetail,
                                timestamp = System.currentTimeMillis()
                            )

                            database.chatMessageDao().insertMessage(errorMsg)
                        }
                        keepGoing = false
                    }

                    if (proposedCommand.isNullOrEmpty()) {
                        agentProgress.value = AgentProgress(
                            phase = AgentPhase.COMPLETED,
                            message = "Instrucción completada"
                        )
                        keepGoing = false
                    } else {
                        val cmd = proposedCommand!!

                        agentProgress.value = AgentProgress(
                            phase = AgentPhase.EXECUTING,
                            message = "Ejecutando comando...",
                            currentAttempt = attempt,
                            maxAttempts = maxAttempts,
                            currentCommand = cmd
                        )

                        val cmdResult = executeAndAwaitCommand(cmd)

                        if (cmdResult.exitCode == 0) {
                            agentProgress.value = AgentProgress(
                                phase = AgentPhase.COMPLETED,
                                message = "Ejecución completada con éxito",
                                currentAttempt = attempt,
                                maxAttempts = maxAttempts,
                                currentCommand = cmd
                            )
                            keepGoing = false
                        } else {
                            // Smart client-side path/directory/capitalization retry
                            var autocorrectedCmd: String? = null
                            val lowerCmd = cmd.lowercase()
                            val isPathError = cmdResult.stderr.contains("no such file", ignoreCase = true) ||
                                              cmdResult.stderr.contains("not a directory", ignoreCase = true) ||
                                              cmdResult.stderr.contains("no existe", ignoreCase = true)

                            if (isPathError) {
                                when {
                                    lowerCmd.contains("download") && !cmd.contains("Download") -> {
                                        autocorrectedCmd = cmd.replace("download", "Download")
                                    }
                                    lowerCmd.contains("document") && !cmd.contains("Documents") -> {
                                        autocorrectedCmd = cmd.replace("document", "Documents")
                                    }
                                    !cmd.startsWith("/") && !cmd.startsWith("$") && !cmd.startsWith(".") -> {
                                        if (cmd.startsWith("cd ")) {
                                            val dir = cmd.substringAfter("cd ").trim()
                                            autocorrectedCmd = "cd /storage/emulated/0/$dir 2>/dev/null || cd \$HOME/storage/shared/$dir"
                                        }
                                    }
                                }
                            }

                            if (autocorrectedCmd != null) {
                                agentProgress.value = AgentProgress(
                                    phase = AgentPhase.RETRYING,
                                    message = "Autocorrigiendo ruta de archivo...",
                                    currentAttempt = attempt,
                                    maxAttempts = maxAttempts,
                                    currentCommand = autocorrectedCmd
                                )
                                insertSystemMessage("Helix detectó error de ruta. Probando variante corregida: $autocorrectedCmd")
                                val retryResult = executeAndAwaitCommand(autocorrectedCmd)
                                if (retryResult.exitCode == 0) {
                                    agentProgress.value = AgentProgress(
                                        phase = AgentPhase.COMPLETED,
                                        message = "Variante ejecutada con éxito",
                                        currentAttempt = attempt,
                                        maxAttempts = maxAttempts,
                                        currentCommand = autocorrectedCmd
                                    )
                                    keepGoing = false
                                    continue
                                }
                            }

                            attempt++
                            if (attempt <= maxAttempts) {
                                agentProgress.value = AgentProgress(
                                    phase = AgentPhase.RETRYING,
                                    message = "Comando falló. Buscando alternativa...",
                                    currentAttempt = attempt,
                                    maxAttempts = maxAttempts,
                                    currentCommand = cmd
                                )
                                currentPrompt = """
                                El comando anterior que propusiste:
                                `$cmd`

                                Falló con el código de salida ${cmdResult.exitCode}.
                                Detalle del error (stderr/stdout):
                                ${cmdResult.stderr.ifBlank { cmdResult.stdout }}

                                Analiza por qué falló (por ejemplo, diferencias de mayúsculas/minúsculas en rutas de Termux, ubicación actual incorrecta o falta de permisos) y propón un comando alternativo inteligente que sí funcione. Describe brevemente qué detectaste y qué intentarás ahora, y luego añade al final el comando corregido en el tag obligatorio [EXECUTE: comando corregido].
                                """.trimIndent()
                            } else {
                                agentProgress.value = AgentProgress(
                                    phase = AgentPhase.FAILED,
                                    message = "El proceso falló después de $maxAttempts intentos",
                                    currentAttempt = attempt,
                                    maxAttempts = maxAttempts,
                                    currentCommand = cmd
                                )
                                insertSystemMessage("Helix no pudo completar la operación tras $maxAttempts intentos.")
                                keepGoing = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                agentProgress.value = AgentProgress(
                    phase = AgentPhase.FAILED,
                    message = "Error en el agente: ${e.localizedMessage}"
                )
            } finally {
                // Ensure we clean job reference
                if (agentJob?.isCancelled == false) {
                    agentProgress.value = AgentProgress(
                        phase = AgentPhase.IDLE,
                        message = ""
                    )
                }
                agentJob = null
            }
        }
    }

    private suspend fun executeTermuxCommand(command: String) {
        insertSystemMessage("Ejecutando en Termux: $command")

        val socket = activeWebSocket
        if (socket != null && webSocketState.value == ConnectionState.CONNECTED) {
            lastStreamedMessageId = null
            val sent = socket.send(command)

            if (!sent) {
                insertSystemMessage("No se pudo enviar el comando por WebSocket.")
            }
        } else {
            insertSystemMessage(
                "No se pudo ejecutar \"$command\" porque la conexión con Termux no está disponible."
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.chatMessageDao().clearAllMessages()
            }

            lastStreamedMessageId = null
            insertSystemMessage("La conversación se limpió correctamente.")
        }
    }
}