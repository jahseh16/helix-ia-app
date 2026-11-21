package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AgentPhase
import com.example.ui.ChatViewModel
import com.example.ui.components.*
import com.example.ui.settings.*
import com.example.ui.theme.*

@Composable
fun AgentProgressCard(viewModel: ChatViewModel) {
    val progress by viewModel.agentProgress.collectAsStateWithLifecycle()

    val isExecuting = progress.phase != AgentPhase.IDLE &&
                      progress.phase != AgentPhase.COMPLETED &&
                      progress.phase != AgentPhase.FAILED &&
                      progress.phase != AgentPhase.CANCELLED

    AnimatedVisibility(
        visible = isExecuting,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = AppSurface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (progress.phase == AgentPhase.RETRYING) AppDanger.copy(alpha = 0.35f) else AppAccent.copy(alpha = 0.35f)),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (progress.phase == AgentPhase.RETRYING) AppDanger else AppCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = progress.message,
                        style = BodyStyle.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppText
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (progress.maxAttempts > 1) {
                        Text(
                            text = "Intento ${progress.currentAttempt} de ${progress.maxAttempts}",
                            style = MetaStyle.copy(
                                fontSize = 11.sp,
                                color = AppAccentSoft
                            )
                        )
                    }
                }

                if (progress.currentCommand.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AppStroke)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$",
                                style = BodyStyle.copy(
                                    color = AppCyan,
                                    fontFamily = UiMono,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = progress.currentCommand,
                                style = BodyStyle.copy(
                                    color = AppText,
                                    fontFamily = UiMono,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "progress")
                    val progressWidth by infiniteTransition.animateFloat(
                        initialValue = 0.10f,
                        targetValue = 0.90f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "progress"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (progress.phase == AgentPhase.RETRYING) {
                                        listOf(AppDanger, Color(0xFFE57373))
                                    } else {
                                        listOf(AppAccent, AppCyan)
                                    }
                                )
                            )
                    )
                }
            }
        }
    }
}
