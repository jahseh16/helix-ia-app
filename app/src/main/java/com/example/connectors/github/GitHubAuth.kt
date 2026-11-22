package com.example.connectors.github

import com.example.agent.tools.executeCancellable
import com.example.connectors.retryOnNetworkError
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int
)

open class DeviceFlowException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Fallo de red (DNS, sin conexión...) tras agotar los reintentos; durante el sondeo no es definitivo. */
class DeviceFlowNetworkException(message: String, cause: Throwable? = null) : DeviceFlowException(message, cause)

/**
 * OAuth Device Flow de GitHub (sin Client Secret: el Client ID es público y el flujo está pensado para
 * apps instaladas). https://docs.github.com/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow
 */
class DeviceFlowClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://github.com",
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun requestDeviceCode(clientId: String, scope: String): DeviceCode {
        val body = FormBody.Builder().add("client_id", clientId).add("scope", scope).build()
        val json = post("/login/device/code", body, notFoundMessage = NOT_FOUND_CLIENT)
        json.optString("error").takeIf { it.isNotEmpty() }?.let { throw DeviceFlowException(describe(it, json)) }
        val device = json.optString("device_code")
        val user = json.optString("user_code")
        val uri = json.optString("verification_uri")
        if (device.isEmpty() || user.isEmpty() || uri.isEmpty()) throw DeviceFlowException("GitHub devolvió una respuesta incompleta")
        return DeviceCode(device, user, uri, json.optInt("expires_in", 900), json.optInt("interval", 5).coerceAtLeast(1))
    }

    /**
     * Consulta el token respetando el `interval` de GitHub: espera entre intentos, suma 5 s si responde
     * `slow_down` y termina si el código caduca o el usuario deniega. Es cancelable (la corrutina).
     */
    suspend fun pollForToken(clientId: String, code: DeviceCode): String {
        var interval = code.intervalSeconds
        var networkFailures = 0
        val deadline = now() + code.expiresInSeconds * 1000L
        while (true) {
            sleep(interval * 1000L)
            if (now() > deadline) throw DeviceFlowException("El código caducó. Vuelve a pulsar Conectar.")
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", code.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val json = try {
                post("/login/oauth/access_token", body, notFoundMessage = NOT_FOUND_CLIENT)
            } catch (e: DeviceFlowNetworkException) {
                // Sin red un momento (p. ej. al volver del navegador): se sigue esperando en vez de abortar
                if (++networkFailures >= MAX_POLL_NETWORK_FAILURES) throw e
                continue
            }
            networkFailures = 0
            val token = json.optString("access_token")
            if (token.isNotEmpty()) return token
            when (val error = json.optString("error")) {
                "authorization_pending", "" -> Unit
                "slow_down" -> interval = json.optInt("interval", interval + 5).coerceAtLeast(interval + 1)
                else -> throw DeviceFlowException(describe(error, json))
            }
        }
    }

    private suspend fun post(path: String, body: FormBody, notFoundMessage: String): JSONObject {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Accept", "application/json")
            .header("User-Agent", "HelixAgent")
            .post(body)
            .build()
        return try {
            retryOnNetworkError(sleep = sleep) { http.newCall(request).executeCancellable { r ->
                val text = r.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                when {
                    r.code == 404 -> throw DeviceFlowException(notFoundMessage)
                    r.code == 429 -> throw DeviceFlowException("GitHub limitó las peticiones (HTTP 429). Espera un minuto.")
                    json == null -> throw DeviceFlowException("Respuesta no válida de GitHub (HTTP ${r.code})")
                    else -> json
                }
            } }
        } catch (e: IOException) {
            throw DeviceFlowNetworkException("No se pudo conectar con GitHub: ${e.message ?: "error de red"}", e)
        }
    }

    private fun describe(error: String, json: JSONObject): String = when (error) {
        "access_denied" -> "Cancelaste la autorización en GitHub."
        "expired_token" -> "El código caducó. Vuelve a pulsar Conectar."
        "device_flow_disabled" ->
            "El Device Flow no está activado en la OAuth App de GitHub. Actívalo en Settings → Developer settings → OAuth Apps → «Enable Device Flow»."
        "incorrect_client_credentials", "unauthorized_client" ->
            "GitHub no acepta este Client ID. Revisa que la OAuth App exista y tenga el Device Flow activado."
        else -> json.optString("error_description").ifEmpty { "GitHub respondió con error: $error" }
    }

    companion object {
        private const val MAX_POLL_NETWORK_FAILURES = 6
        const val NOT_FOUND_CLIENT =
            "GitHub no reconoce el Client ID (HTTP 404). Revisa que la OAuth App exista y tenga «Enable Device Flow» activado."
    }
}
