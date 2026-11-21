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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.database.ChatMessageEntity
import com.example.ui.theme.AppAccent
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
fun ChatMessageBubble(message: ChatMessageEntity) {
    when (message.sender) {
        "USER" -> UserBubble(message.text)
        "AI" -> AiBubble(text = message.text, modelUsed = message.modelUsed)
        else -> SystemNote(message.text)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = UserBubbleShape,
            color = AppBubbleUser,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            MessageContentWithCodeBlocks(text = text, isUser = true)
        }
    }
}

@Composable
private fun AiBubble(text: String, modelUsed: String?) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        ModelLabel(modelUsed ?: "Asistente")
        Surface(
            shape = AiBubbleShape,
            color = AppSurface,
            border = BorderStroke(1.dp, AppStroke),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 20.dp)
        ) {
            MessageContentWithCodeBlocks(text = text, isUser = false)
        }
    }
}

@Composable
private fun ModelLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(AppAccent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MetaStyle.copy(color = AppAccentSoft, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SystemNote(text: String) {
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

/** Indicador "Helix está pensando": tres puntos que pulsan en secuencia. */
@Composable
fun ThinkingBubble() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        ModelLabel("Pensando")
        Surface(
            shape = AiBubbleShape,
            color = AppSurface,
            border = BorderStroke(1.dp, AppStroke)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
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
}
