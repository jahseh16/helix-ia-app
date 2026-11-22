package com.example.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import com.example.ui.ModelCatalog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppAccent
import com.example.ui.theme.AppDanger
import com.example.ui.theme.AppAccentSoft
import com.example.ui.theme.AppBubbleUser
import com.example.ui.theme.AppStroke
import com.example.ui.theme.AppSurface
import com.example.ui.theme.AppSurface2
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.MetaStyle

private val UserBubbleShape = RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp)
private val AiBubbleShape = RoundedCornerShape(6.dp, 24.dp, 24.dp, 24.dp)

@Composable
fun UserBubble(text: String, imagePath: String? = null, hasCloudImage: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (imagePath != null || hasCloudImage) AttachedImage(imagePath)
        if (text.isNotBlank()) {
            Surface(
                shape = UserBubbleShape,
                color = AppBubbleUser,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                MessageContentWithCodeBlocks(text = text, isUser = true)
            }
        }
    }
}

/** Imagen adjunta de un mensaje (archivo local ya comprimido). Si aún no se ha bajado de la nube, un marcador. */
@Composable
private fun AttachedImage(path: String?) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = path?.let { p ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { android.graphics.BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val bmp = bitmap
    Surface(shape = RoundedCornerShape(20.dp), color = AppSurface2, modifier = Modifier.widthIn(max = 260.dp).testTag("message_image")) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = "Imagen adjunta",
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text("Imagen guardada en la nube", style = MetaStyle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
        }
    }
}

/**
 * Respuesta del asistente: SIN globo ni tarjeta, directamente sobre el fondo del chat y a todo el ancho.
 * Solo los mensajes del usuario llevan burbuja. [showCopy] añade el botón de copiar al pie.
 */
@Composable
fun AiMessageText(text: String, showCopy: Boolean, messageId: Long? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        MessageContentWithCodeBlocks(text = text, isUser = false)
        if (showCopy) {
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CopyButton(
                    textToCopy = text,
                    modifier = Modifier.testTag("copy_response_button"),
                    description = "Copiar respuesta completa"
                )
                if (messageId != null) SpeakButton(messageId, text)
            }
            if (messageId != null) SpeakError(messageId)
        }
    }
}

/** Etiqueta de un mensaje del asistente: logo + nombre limpio (nunca el ID técnico). */
@Composable
fun ModelLabel(raw: String) {
    val model = remember(raw) { ModelCatalog.resolveStored(raw) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
    ) {
        if (model != null) {
            BrandLogo(model.brand, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppAccent))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = model?.name ?: raw,
            style = MetaStyle.copy(color = AppAccentSoft, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun SystemNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = AppSurface2) {
            Text(
                text = text,
                style = MetaStyle.copy(color = AppTextMuted),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ErrorNote(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppDanger.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AppDanger.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MetaStyle.copy(color = AppDanger, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/** Indicador "Helix está pensando": tres puntos que pulsan en secuencia. */
@Composable
fun ThinkingBubble() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        ModelLabel("Pensando")
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val transition = rememberInfiniteTransition(label = "thinking")
            repeat(3) { index ->
                val dotAlpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1200
                            0.25f at index * 150 using LinearEasing
                            1f at index * 150 + 300 using LinearEasing
                            0.25f at index * 150 + 600 using LinearEasing
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "dot_$index"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(dotAlpha)
                        .clip(CircleShape)
                        .background(AppAccentSoft)
                )
            }
        }
    }
}
