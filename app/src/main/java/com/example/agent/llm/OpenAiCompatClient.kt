package com.example.agent.llm

import com.example.agent.model.LlmRequest
import com.example.agent.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Cliente de streaming para cualquier API compatible con OpenAI `/chat/completions`. */
class OpenAiCompatClient(
    private val http: OkHttpClient,
    private val url: String,
    private val apiKey: String,
    private val extraHeaders: Map<String, String> = emptyMap()
) : LlmClient {

    override fun stream(request: LlmRequest): Flow<StreamEvent> = callbackFlow {
        if (apiKey.isBlank()) {
            close(IllegalStateException("La API key está vacía. Configúrala en Ajustes."))
            return@callbackFlow
        }

        val body = OpenAiRequestBuilder.build(request).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
            .build()

        val call = http.newCall(httpRequest)

        val reader = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw LlmApiException(response.code, errorDetail(response.body?.string().orEmpty()))
                    }
                    val source = response.body?.source() ?: throw LlmStreamException("Respuesta vacía del proveedor")
                    val parser = SseParser()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        parser.feed(line).forEach { send(it) }
                    }
                    parser.finish().forEach { send(it) }
                }
                close()
            } catch (e: Throwable) {
                close(e)
            }
        }

        // Cancelar la colección aborta la llamada aunque el hilo esté bloqueado leyendo el socket
        awaitClose {
            call.cancel()
            reader.cancel()
        }
    }

    private fun errorDetail(raw: String): String {
        if (raw.isBlank()) return "Sin detalle adicional"
        return try {
            val err = JSONObject(raw).opt("error")
            when (err) {
                is JSONObject -> err.optString("message").ifBlank { raw }
                is String -> err
                else -> raw
            }
        } catch (e: Exception) {
            raw
        }.take(600)
    }

    companion object {
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

        fun forProvider(provider: String, apiKey: String, http: OkHttpClient): OpenAiCompatClient =
            when (provider) {
                "Groq" -> OpenAiCompatClient(http, GROQ_URL, apiKey)
                else -> OpenAiCompatClient(
                    http, OPENROUTER_URL, apiKey,
                    mapOf("HTTP-Referer" to "https://github.com/HelixApp", "X-Title" to "Helix App")
                )
            }
    }
}
