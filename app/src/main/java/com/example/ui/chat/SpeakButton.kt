package com.example.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.TtsPhase
import com.example.tts.TtsState
import com.example.ui.theme.*

/** Estado del reproductor de voz y acciones, disponibles para cualquier mensaje del asistente. */
class TtsUi(val state: TtsState, val onToggle: (Long, String) -> Unit, val onStop: () -> Unit)

val LocalTts = compositionLocalOf<TtsUi?> { null }

/** Botón Escuchar / Pausar / Reanudar de un mensaje, con estado visual (cargando, sonando, en pausa). */
@Composable
fun SpeakButton(messageId: Long, text: String, modifier: Modifier = Modifier) {
    val tts = LocalTts.current ?: return
    val phase = tts.state.phaseOf(messageId)
    val active = phase == TtsPhase.PLAYING || phase == TtsPhase.PAUSED || phase == TtsPhase.LOADING
    val tint = when (phase) {
        TtsPhase.PLAYING, TtsPhase.LOADING -> AppAccentSoft
        TtsPhase.PAUSED -> AppWarning
        TtsPhase.ERROR -> AppDanger
        TtsPhase.IDLE -> AppTextMuted
    }
    val label = when (phase) {
        TtsPhase.IDLE -> "Escuchar"
        TtsPhase.LOADING -> "Cargando…"
        TtsPhase.PLAYING -> "Pausar"
        TtsPhase.PAUSED -> "Reanudar"
        TtsPhase.ERROR -> "Reintentar"
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable { tts.onToggle(messageId, text) }
                .padding(horizontal = 8.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "$label respuesta en voz alta"
                }
                .testTag("speak_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (phase) {
                TtsPhase.LOADING -> CircularProgressIndicator(modifier = Modifier.size(15.dp), color = tint, strokeWidth = 2.dp)
                else -> Icon(
                    imageVector = when (phase) {
                        TtsPhase.PLAYING -> Icons.Default.Pause
                        TtsPhase.PAUSED -> Icons.Default.PlayArrow
                        TtsPhase.ERROR -> Icons.Default.ErrorOutline
                        else -> Icons.Default.VolumeUp
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(label, style = MetaStyle.copy(color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
        if (active) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { tts.onStop() }
                    .padding(horizontal = 8.dp)
                    .semantics { role = Role.Button; contentDescription = "Detener audio" }
                    .testTag("stop_speech_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = AppTextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Texto de error de la voz bajo la respuesta, si el último intento de este mensaje falló. */
@Composable
fun SpeakError(messageId: Long) {
    val tts = LocalTts.current ?: return
    if (tts.state.phaseOf(messageId) == TtsPhase.ERROR) {
        Text(
            tts.state.error ?: "No se pudo reproducir el audio",
            style = MetaStyle.copy(color = AppDanger, fontSize = 12.sp),
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
}
