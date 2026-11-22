package com.example.connectors.github

import com.example.connectors.Connector
import com.example.connectors.ConnectorAccount
import com.example.connectors.ConnectorState
import com.example.data.security.SecureStore
import com.example.data.security.SecureStoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Conector de GitHub con OAuth Device Flow. El access_token y los datos del usuario se guardan cifrados en
 * [SecureStore] (AES-256-GCM, Keystore); al desconectar se borran. No hay Client Secret en la app.
 */
class GitHubConnector(
    private val store: SecureStore,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val dir: File,
    private val api: () -> GitHubApi,
    private val flow: DeviceFlowClient = DeviceFlowClient(http),
    private val clientId: String = CLIENT_ID
) : Connector {
    override val id = "github"
    override val title = "GitHub"
    override val description = "Conecta tus repositorios para revisar y mejorar código con @ o /"
    override val iconKey = "github"

    private val _state = MutableStateFlow<ConnectorState>(ConnectorState.Disconnected)
    override val state: StateFlow<ConnectorState> = _state

    @Volatile
    var token: String? = store.getString(KEY_TOKEN)
        private set

    private var job: Job? = null

    init {
        // Sesión guardada de una ejecución anterior
        if (!token.isNullOrBlank()) {
            val saved = store.getString(KEY_USER)?.let { runCatching { JSONObject(it) }.getOrNull() }
            _state.value = if (saved != null) {
                ConnectorState.Connected(
                    ConnectorAccount(
                        login = saved.optString("login"),
                        displayName = saved.optString("name").ifEmpty { null },
                        avatarPath = saved.optString("avatar").takeIf { it.isNotEmpty() && File(it).isFile }
                    )
                )
            } else ConnectorState.Disconnected
        }
    }

    val isConnected: Boolean get() = _state.value is ConnectorState.Connected
    val login: String? get() = (_state.value as? ConnectorState.Connected)?.account?.login

    override fun connect(onUserCode: (String) -> Unit) {
        if (_state.value is ConnectorState.Connecting) return
        job?.cancel()
        _state.value = ConnectorState.Connecting()
        job = scope.launch {
            try {
                val code = flow.requestDeviceCode(clientId, SCOPES)
                _state.value = ConnectorState.Connecting(code.userCode, code.verificationUri)
                onUserCode(code.userCode)

                val accessToken = flow.pollForToken(clientId, code)
                val user = api().user(accessToken)
                val avatar = user.avatarUrl?.let { downloadAvatar(it) }

                try {
                    store.putString(KEY_TOKEN, accessToken)
                    store.putString(KEY_USER, JSONObject().put("login", user.login).put("name", user.name ?: "").put("avatar", avatar ?: "").toString())
                } catch (e: SecureStoreException) {
                    store.remove(KEY_TOKEN); store.remove(KEY_USER)
                    _state.value = ConnectorState.Failed("No se pudo guardar el acceso de forma segura en este dispositivo.")
                    return@launch
                }
                token = accessToken
                _state.value = ConnectorState.Connected(ConnectorAccount(user.login, user.name, avatar))
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeviceFlowException) {
                _state.value = ConnectorState.Failed(e.message ?: "No se pudo conectar con GitHub")
            } catch (e: GitHubException) {
                _state.value = ConnectorState.Failed(e.message ?: "No se pudo conectar con GitHub")
            } catch (e: Exception) {
                _state.value = ConnectorState.Failed("No se pudo conectar con GitHub: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        if (_state.value is ConnectorState.Connecting) _state.value = ConnectorState.Disconnected
    }

    override fun disconnect() {
        job?.cancel()
        job = null
        token = null
        store.remove(KEY_TOKEN)
        store.remove(KEY_USER)
        dir.listFiles()?.filter { it.name.startsWith("github_avatar") }?.forEach { it.delete() }
        _state.value = ConnectorState.Disconnected
    }

    /** GitHub respondió 401: el token ya no sirve; se descarta como si el usuario hubiera desconectado. */
    fun onTokenRevoked() {
        if (token != null) disconnect()
    }

    private suspend fun downloadAvatar(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", "HelixAgent").build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val bytes = r.body?.source()?.let { src ->
                    src.request(MAX_AVATAR + 1)
                    if (src.buffer.size > MAX_AVATAR) return@use null
                    src.readByteArray()
                } ?: return@use null
                dir.mkdirs()
                dir.listFiles()?.filter { it.name.startsWith("github_avatar") }?.forEach { it.delete() }
                File(dir, "github_avatar_${System.currentTimeMillis()}.img").also { it.writeBytes(bytes) }.path
            }
        }.getOrNull()
    }

    companion object {
        /** Client ID público de la OAuth App de Helix (el Device Flow no usa Client Secret). */
        const val CLIENT_ID = "Ov23liJUiMD3GU92HfLf"
        const val SCOPES = "repo read:user"
        const val KEY_TOKEN = "github_access_token"
        const val KEY_USER = "github_user"
        private const val MAX_AVATAR = 2L * 1024 * 1024
    }
}
