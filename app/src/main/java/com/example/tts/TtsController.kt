package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class TtsPhase { IDLE, LOADING, PLAYING, PAUSED, ERROR }

/** Estado del reproductor: solo un mensaje a la vez suena (o carga). */
data class TtsState(val messageId: Long? = null, val phase: TtsPhase = TtsPhase.IDLE, val error: String? = null) {
    fun phaseOf(id: Long): TtsPhase = if (messageId == id) phase else TtsPhase.IDLE
}

enum class TtsAction { START, PAUSE, RESUME, STOP }

/** Qué hace el botón de un mensaje según el estado actual (lógica pura, probada en JVM). */
fun nextTtsAction(state: TtsState, messageId: Long): TtsAction = when (state.phaseOf(messageId)) {
    TtsPhase.PLAYING -> TtsAction.PAUSE
    TtsPhase.PAUSED -> TtsAction.RESUME
    TtsPhase.LOADING -> TtsAction.STOP
    TtsPhase.IDLE, TtsPhase.ERROR -> TtsAction.START
}

/** La key del archivo de secretos tiene prioridad; si está vacía se usa la guardada en Ajustes (SecureStore). */
fun resolveFishKey(fromSecretsFile: String, fromSettings: String): String =
    fromSecretsFile.trim().ifEmpty { fromSettings.trim() }

/**
 * Sintetiza con Fish Audio y reproduce con [MediaPlayer]. El mp3 se cachea por texto+voz, así
 * volver a escuchar un mensaje no vuelve a consumir la API.
 */
class TtsController(
    context: Context,
    private val scope: CoroutineScope,
    private val client: FishAudioClient,
    private val keyProvider: () -> String,
    private val voiceProvider: () -> String
) {
    private val cacheDir = File(context.applicationContext.cacheDir, "tts").also { it.mkdirs() }
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state

    private var player: MediaPlayer? = null
    private var job: Job? = null

    fun toggle(messageId: Long, markdown: String) {
        when (nextTtsAction(_state.value, messageId)) {
            TtsAction.PAUSE -> runCatching { player?.pause() }.also { _state.value = TtsState(messageId, TtsPhase.PAUSED) }
            TtsAction.RESUME -> runCatching { player?.start() }.also { _state.value = TtsState(messageId, TtsPhase.PLAYING) }
            TtsAction.STOP -> stop()
            TtsAction.START -> start(messageId, markdown)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        releasePlayer()
        _state.value = TtsState()
    }

    fun release() = stop()

    private fun start(messageId: Long, markdown: String) {
        stop()
        val text = SpeechText.clean(markdown)
        if (text.isBlank()) {
            _state.value = TtsState(messageId, TtsPhase.ERROR, "No hay texto que leer en este mensaje")
            return
        }
        _state.value = TtsState(messageId, TtsPhase.LOADING)
        job = scope.launch {
            try {
                val voice = voiceProvider()
                val file = withContext(Dispatchers.IO) {
                    val cached = File(cacheDir, sha256("$voice|$text") + ".mp3")
                    if (cached.isFile && cached.length() > 0) cached
                    else {
                        val bytes = client.synthesize(text, keyProvider(), voice)
                        File(cacheDir, cached.name + ".tmp").also { it.writeBytes(bytes) }.renameTo(cached)
                        cached
                    }
                }
                play(messageId, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TtsException) {
                _state.value = TtsState(messageId, TtsPhase.ERROR, e.message)
            } catch (e: Exception) {
                _state.value = TtsState(messageId, TtsPhase.ERROR, "No se pudo generar el audio: ${e.message}")
            }
        }
    }

    private fun play(messageId: Long, file: File) {
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(file.path)
            mp.setOnPreparedListener {
                if (player === mp) {
                    it.start()
                    _state.value = TtsState(messageId, TtsPhase.PLAYING)
                }
            }
            mp.setOnCompletionListener {
                if (player === mp) {
                    releasePlayer()
                    _state.value = TtsState()
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) {
                    releasePlayer()
                    file.delete() // un mp3 corrupto no debe quedarse en caché
                    _state.value = TtsState(messageId, TtsPhase.ERROR, "No se pudo reproducir el audio")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            _state.value = TtsState(messageId, TtsPhase.ERROR, "No se pudo reproducir el audio")
        }
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.reset(); it.release() } }
        player = null
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
}
