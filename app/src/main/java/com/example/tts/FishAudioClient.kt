package com.example.tts

import com.example.agent.tools.executeCancellable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/** Voz de Fish Audio seleccionable en Ajustes de TTS. */
data class TtsVoice(val id: String, val name: String, val description: String)

object TtsVoices {
    val ANIME = TtsVoice("60525c84e0a142db9af4932408eca0cc", "Anime tierna", "Voz anime dulce (predeterminada)")
    val SPANISH = TtsVoice("2116540cce1e462cbbcc1a4225ba46cc", "Tierna en español", "Voz suave en español")
    val KURISU = TtsVoice("b450b19370434173b121446057622e9b", "Makise Kurisu", "Personaje de Steins;Gate")

    val all = listOf(ANIME, SPANISH, KURISU)
    val default = ANIME

    fun byId(id: String?): TtsVoice = all.firstOrNull { it.id == id } ?: default
}

class TtsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Cliente de `POST /v1/tts` de Fish Audio. Devuelve el audio mp3 como bytes. */
class FishAudioClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.fish.audio"
) {
    suspend fun synthesize(
        text: String,
        apiKey: String,
        referenceId: String = TtsVoices.default.id,
        model: String = MODEL
    ): ByteArray {
        if (apiKey.isBlank()) throw TtsException("Falta la API key de Fish Audio en helix_secrets.json")
        if (text.isBlank()) throw TtsException("No hay texto que leer")

        val body = JSONObject()
            .put("text", text)
            .put("reference_id", referenceId)
            .put("format", "mp3")
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/tts")
            .header("Authorization", "Bearer $apiKey")
            .header("model", model)
            .post(body)
            .build()

        return try {
            http.newCall(request).executeCancellable { resp ->
                if (!resp.isSuccessful) throw TtsException(errorFor(resp.code))
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) throw TtsException("Fish Audio devolvió un audio vacío")
                bytes
            }
        } catch (e: TtsException) {
            throw e
        } catch (e: IOException) {
            throw TtsException("No se pudo conectar con Fish Audio: ${e.message ?: "error de red"}", e)
        }
    }

    companion object {
        const val MODEL = "s2.1-pro-free"

        fun errorFor(code: Int): String = when (code) {
            401, 403 -> "La API key de Fish Audio no es válida (HTTP $code)"
            402 -> "Sin saldo en Fish Audio (HTTP 402)"
            429 -> "Límite de uso de Fish Audio alcanzado (HTTP 429). Espera un momento"
            else -> "Fish Audio respondió con error HTTP $code"
        }
    }
}
