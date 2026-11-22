package com.example.data.security

import android.content.Context
import org.json.JSONObject

/**
 * Secretos locales del desarrollador, leídos de `app/src/main/assets/helix_secrets.json`
 * (ignorado por git, nunca se sube al repositorio). Tienen prioridad sobre lo guardado en Ajustes.
 */
data class Secrets(val fishAudioApiKey: String = "") {
    companion object {
        const val ASSET_NAME = "helix_secrets.json"
        const val KEY_FISH = "fish_audio_api_key"

        /** Interpreta el JSON; cualquier fallo o valor de ejemplo/vacío da una clave vacía. */
        fun parse(json: String?): Secrets {
            if (json.isNullOrBlank()) return Secrets()
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return Secrets()
            val fish = obj.optString(KEY_FISH, "").trim()
            return Secrets(fishAudioApiKey = if (fish.startsWith("PEGA_")) "" else fish)
        }

        fun load(context: Context): Secrets = runCatching {
            parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
        }.getOrDefault(Secrets())
    }
}
