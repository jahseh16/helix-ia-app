package com.example.vision

import android.util.Base64
import com.example.connectors.retryOnNetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class VlmException(message: String) : Exception(message)

/**
 * Visión con vlm.run (API compatible con OpenAI). Por defecto funciona SIN API key (modo gratis);
 * si [keyProvider] devuelve una key personal se envía como `Authorization: Bearer`.
 *
 * La imagen viaja como `data:` URL solo dentro de esta petición HTTP; no se guarda en ningún sitio.
 */
class VlmRunClient(
    private val http: OkHttpClient,
    private val keyProvider: () -> String? = { null },
    private val url: String = DEFAULT_URL,
    private val model: String = DEFAULT_MODEL
) {
    suspend fun describe(jpeg: ByteArray, userText: String): String = withContext(Dispatchers.IO) {
        val body = buildRequest(model, buildPrompt(userText), Base64.encodeToString(jpeg, Base64.NO_WRAP))
        val builder = Request.Builder().url(url).post(body.toRequestBody(JSON))
        keyProvider()?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.header("Authorization", "Bearer $it") }
        val request = builder.build()

        val (code, text) = try {
            retryOnNetworkError { http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() } }
        } catch (e: IOException) {
            throw VlmException("No se pudo conectar con vlm.run. Revisa tu conexión.")
        }
        if (code !in 200..299) throw VlmException(errorFor(code, text, hasKey = !keyProvider().isNullOrBlank()))
        parseAnswer(text)
    }

    companion object {
        const val DEFAULT_URL = "https://gateway.vlm.run/v1/openai/chat/completions"
        const val DEFAULT_MODEL = "qwen/qwen3.5-0.8b"
        private val JSON = "application/json".toMediaType()
        private val THINK = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)

        fun buildPrompt(userText: String): String {
            val ask = userText.trim()
            return if (ask.isEmpty()) {
                "Describe esta imagen con detalle, en español. Menciona objetos, personas, colores y el contexto. " +
                    "Si hay texto visible, transcríbelo literalmente."
            } else {
                "Observa la imagen y responde en español con todos los detalles útiles (incluido cualquier texto visible). " +
                    "Pregunta del usuario: $ask"
            }
        }

        fun buildRequest(model: String, prompt: String, base64Jpeg: String): String =
            JSONObject()
                .put("model", model)
                .put("max_tokens", 700)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "text").put("text", prompt))
                                .put(
                                    JSONObject().put("type", "image_url")
                                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Jpeg"))
                                )
                        )
                    )
                ).toString()

        /** Extrae el texto de la respuesta y quita un posible bloque `<think>…</think>` de modelos que razonan. */
        fun parseAnswer(json: String): String {
            val message = try {
                JSONObject(json).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            } catch (e: Exception) {
                throw VlmException("vlm.run devolvió una respuesta que no se pudo leer.")
            }
            val content = message.opt("content")
            val raw = when (content) {
                is String -> content
                is JSONArray -> (0 until content.length()).joinToString("") { content.optJSONObject(it)?.optString("text").orEmpty() }
                else -> ""
            }
            val clean = THINK.replace(raw, "").trim()
            if (clean.isEmpty()) throw VlmException("El modelo de visión no devolvió ninguna descripción.")
            return clean
        }

        fun errorFor(code: Int, body: String, hasKey: Boolean): String {
            val detail = runCatching {
                JSONObject(body).let { o -> o.optJSONObject("error")?.optString("message") ?: o.optString("detail") }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.take(160)
            return when (code) {
                401, 403 -> if (hasKey) "vlm.run rechazó tu API key. Revísala en Ajustes o bórrala para usar el modo gratis."
                else "vlm.run pide una API key. Añade la tuya en Ajustes → Visión."
                402 -> "Tu cuenta de vlm.run se quedó sin crédito."
                413 -> "La imagen es demasiado grande para vlm.run."
                429 -> "vlm.run alcanzó el límite del modo gratis. Espera un momento o añade tu API key en Ajustes."
                in 500..599 -> "vlm.run no está disponible ahora mismo (HTTP $code)."
                else -> "vlm.run respondió HTTP $code${detail?.let { ": $it" }.orEmpty()}"
            }
        }
    }
}
