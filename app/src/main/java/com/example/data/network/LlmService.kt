package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val PROVIDER_OPENROUTER = "OpenRouter"
        private const val PROVIDER_GROQ = "Groq"

        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

        private const val APP_REFERER = "https://github.com/HelixApp"
        private const val APP_TITLE = "Helix App"

        private const val DEFAULT_SYSTEM_PROMPT =
            "Eres Helix AI, un asistente técnico de programación conectado al entorno local de Termux del usuario. Responde siempre en español con claridad, precisión y una presentación moderna, limpia y profesional."
    }

    suspend fun getCompletion(
        provider: String,
        apiKey: String,
        modelId: String,
        prompt: String,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                Exception("La API key está vacía. Configúrala en el panel de ajustes.")
            )
        }

        if (modelId.isBlank()) {
            return@withContext Result.failure(
                Exception("No hay un modelo seleccionado.")
            )
        }

        if (prompt.isBlank()) {
            return@withContext Result.failure(
                Exception("El prompt del usuario está vacío.")
            )
        }

        val url = resolveProviderUrl(provider)
        val finalSystemPrompt = buildSystemPrompt(systemPrompt)

        try {
            val messagesArray = JSONArray().apply {
                if (finalSystemPrompt.isNotBlank()) {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", finalSystemPrompt)
                        }
                    )
                }

                put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            }

            val requestBodyJson = JSONObject().apply {
                put("model", modelId)
                put("messages", messagesArray)
                put("temperature", 0.55)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(mediaType))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")

            if (provider == PROVIDER_OPENROUTER) {
                requestBuilder.header("HTTP-Referer", APP_REFERER)
                requestBuilder.header("X-Title", APP_TITLE)
            }

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val providerName = provider.ifBlank { "Proveedor" }
                    val detail = bodyString.ifBlank { "Sin detalle adicional" }
                    return@withContext Result.failure(
                        Exception("$providerName devolvió HTTP ${response.code}. $detail")
                    )
                }

                if (bodyString.isBlank()) {
                    return@withContext Result.failure(
                        Exception("La respuesta del proveedor llegó vacía.")
                    )
                }

                val jsonResponse = JSONObject(bodyString)
                val choices = jsonResponse.optJSONArray("choices")

                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.optJSONObject("message")
                    val content = messageObj?.optString("content").orEmpty()

                    if (content.isNotBlank()) {
                        return@withContext Result.success(content.trim())
                    }
                }

                return@withContext Result.failure(
                    Exception("No se pudo extraer una respuesta válida del modelo. Respuesta recibida: $bodyString")
                )
            }
        } catch (e: IOException) {
            return@withContext Result.failure(
                Exception("Error de red al contactar el proveedor: ${e.localizedMessage}", e)
            )
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Error procesando la respuesta del proveedor: ${e.localizedMessage}", e)
            )
        }
    }

    private fun resolveProviderUrl(provider: String): String {
        return when (provider) {
            PROVIDER_OPENROUTER -> OPENROUTER_URL
            PROVIDER_GROQ -> GROQ_URL
            else -> OPENROUTER_URL
        }
    }

    private fun buildSystemPrompt(basePrompt: String): String {
        val normalizedBasePrompt = if (basePrompt.isBlank()) {
            DEFAULT_SYSTEM_PROMPT
        } else {
            basePrompt.trim()
        }

        return buildString {
            append(normalizedBasePrompt)
            append("\n\n")
            append(
                """
                Instrucciones obligatorias de comportamiento:

                1. Idioma y estilo:
                - Responde siempre en español.
                - Usa un tono profesional, claro, directo y elegante.
                - Evita estética de terminal, exageraciones “cyberpunk” y emojis decorativos innecesarios.
                - La redacción debe sentirse moderna, limpia y de alta calidad.

                2. Ejecución automática de comandos:
                - Si el usuario pide ejecutar un comando, inspeccionar archivos, administrar procesos, usar Git, usar SSH, instalar paquetes, revisar logs o realizar una acción real en Termux, debes inferir el comando correcto y proponer una única acción ejecutable.
                - Para disparar la ejecución automática, agrega EXACTAMENTE al final de tu respuesta:
                  [EXECUTE: comando]
                - Ese tag debe ir al final absoluto de la respuesta, sin texto después.
                - El comando debe ir en una sola línea y sin markdown dentro del tag.

                Ejemplo:
                Voy a revisar el estado de PM2 en tu entorno.
                [EXECUTE: pm2 status]

                3. Bloques de archivos y directorios:
                - Si muestras archivos, carpetas o resultados estructurados tipo listado, NO los devuelvas como tabla cruda de permisos Linux salvo que el usuario lo pida explícitamente.
                - En su lugar, usa un bloque Markdown con el identificador `files`.
                - Presenta la información ordenada, numerada y limpia.

                Ejemplo:
                ```files
                1. 📁 Downloads/ (Directorio)
                2. 📄 index.js (1.2 KB - Script JavaScript)
                3. 📄 package.json (450 B - Configuración)
                4. ⚙️ .env (120 B - Variables)
                ```

                4. Precisión operativa:
                - No inventes resultados de comandos, estados del sistema ni contenidos de archivos.
                - Si una acción requiere verificación real, indica brevemente lo que harás y usa el tag [EXECUTE: ...] cuando corresponda.
                - Si el usuario solo pide explicación, responde sin ejecutar nada.

                5. Formato de respuesta:
                - Prioriza texto limpio y fácil de leer.
                - Usa viñetas o bloques solo cuando ayuden.
                - No uses globos de consola, ruido decorativo ni prefijos innecesarios en cada párrafo.
                """.trimIndent()
            )
        }
    }
}