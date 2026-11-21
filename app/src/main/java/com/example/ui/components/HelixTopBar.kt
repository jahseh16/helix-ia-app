package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ConnectionState
import com.example.ui.chat.*
import com.example.ui.settings.*
import com.example.ui.theme.*

@Composable
fun HelixTopBar(
    wsState: ConnectionState,
    showSettings: Boolean,
    onSettingsToggle: () -> Unit,
    onReconnect: () -> Unit,
    onClearChat: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = when (wsState) {
            ConnectionState.CONNECTED -> AppGreen
            ConnectionState.CONNECTING -> AppCyan
            ConnectionState.DISCONNECTED -> AppTextMuted
            ConnectionState.ERROR -> AppDanger
        },
        label = "status_color"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppSurface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, AppStroke.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Helix",
                    style = TitleStyle.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Chat local con IA",
                    style = MetaStyle
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface3.copy(alpha = 0.85f))
                    .border(
                        1.dp,
                        indicatorColor.copy(alpha = 0.30f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onReconnect() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (wsState) {
                        ConnectionState.CONNECTED -> "Conectado"
                        ConnectionState.CONNECTING -> "Conectando"
                        ConnectionState.DISCONNECTED -> "Desconectado"
                        ConnectionState.ERROR -> "Error"
                    },
                    style = MetaStyle.copy(
                        color = indicatorColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onClearChat,
                modifier = Modifier.testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Chat History",
                    tint = AppTextMuted
                )
            }

            IconButton(
                onClick = onSettingsToggle,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = if (showSettings) Icons.Default.Close else Icons.Default.Settings,
                    contentDescription = "Settings Configuration",
                    tint = if (showSettings) AppDanger else AppAccentSoft
                )
            }
        }
    }
}
