package com.example.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.database.ChatMessageEntity
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*

@Composable
fun CyberLoadingIndicator(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val dotCount by infiniteTransition.animateValue(
        initialValue = 0,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    val progressWidth by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    val dots = ".".repeat(dotCount) + " ".repeat(4 - dotCount)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = AppSurface2.copy(alpha = 0.92f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, AppAccent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = AppCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$text$dots",
                    style = MetaStyle.copy(
                        color = AppText,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(AppAccent, AppCyan)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageEntity) {
    val isUser = message.sender == "USER"
    val isSystem = message.sender == "TERMUX_SYS"
    val isTermuxOut = message.sender == "TERMUX_OUT"
    val isTermuxErr = message.sender == "TERMUX_ERR"

    val isThinking =
        message.sender == "AI" && (message.text.contains("Analyzing matrix") || message.text.startsWith("▌"))
    val isRunning =
        message.text.startsWith("[RUNNING]") ||
            message.text.startsWith("[PROCESS]") ||
            message.text.startsWith("[SSH_CONNECT]")

    if (isThinking) {
        CyberLoadingIndicator(text = "Procesando")
        return
    }

    if (isRunning) {
        val cleanStatus = message.text
            .replace("[RUNNING]", "Ejecutando")
            .replace("[PROCESS]", "Procesando")
            .replace("[SSH_CONNECT]", "Conectando por SSH")
            .trim()
        CyberLoadingIndicator(text = cleanStatus)
        return
    }

    if (message.sender == "AI") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AppAccent, AppCyan))
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.modelUsed ?: "Assistant",
                    style = MetaStyle.copy(
                        color = AppAccentSoft,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 28.dp)
            ) {
                MessageContentWithCodeBlocks(
                    text = message.text,
                    isUser = false,
                    isTermuxOut = false,
                    isTermuxErr = false,
                    isSystem = false
                )
            }
        }
    } else {
        val label = when (message.sender) {
            "USER" -> "Tú"
            "TERMUX_SYS" -> "Sistema"
            "TERMUX_OUT" -> "Termux salida"
            "TERMUX_ERR" -> "Termux error"
            else -> "Dispositivo"
        }

        val labelColor = when (message.sender) {
            "USER" -> AppAccentSoft
            "TERMUX_SYS" -> AppTextMuted
            "TERMUX_OUT" -> AppGreen
            "TERMUX_ERR" -> AppDanger
            else -> AppTextMuted
        }

        val bubbleShape = when {
            isUser -> RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp)
            isSystem -> RoundedCornerShape(18.dp)
            else -> RoundedCornerShape(20.dp)
        }

        val bubbleBg = when {
            isUser -> AppAccent.copy(alpha = 0.14f)
            isSystem -> AppSurface2.copy(alpha = 0.88f)
            isTermuxOut || isTermuxErr -> AppSurface2.copy(alpha = 0.92f)
            else -> AppSurface
        }

        val bubbleBorder = when {
            isUser -> BorderStroke(1.dp, AppAccent.copy(alpha = 0.35f))
            isSystem -> BorderStroke(1.dp, AppStroke)
            isTermuxOut -> BorderStroke(1.dp, AppGreen.copy(alpha = 0.35f))
            isTermuxErr -> BorderStroke(1.dp, AppDanger.copy(alpha = 0.35f))
            else -> BorderStroke(1.dp, AppStroke)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = label,
                style = MetaStyle.copy(
                    color = labelColor,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )

            Surface(
                shape = bubbleShape,
                color = bubbleBg,
                border = bubbleBorder,
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                MessageContentWithCodeBlocks(
                    text = message.text,
                    isUser = isUser,
                    isTermuxOut = isTermuxOut,
                    isTermuxErr = isTermuxErr,
                    isSystem = isSystem
                )
            }
        }
    }
}
