package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ChatMessageEntity
import com.example.ui.theme.AppAccent
import com.example.ui.theme.AppCyan
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.BodyStyle
import com.example.ui.theme.TitleStyle

@Composable
fun ChatHistoryList(messages: List<ChatMessageEntity>, isGenerating: Boolean) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (isGenerating) 1 else 0

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    if (itemCount == 0) {
        EmptyState()
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(items = messages, key = { it.id }) { message ->
            ChatMessageBubble(message = message)
        }
        if (isGenerating) {
            item(key = "thinking") { ThinkingBubble() }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(AppAccent, AppCyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Text(text = "¿En qué te ayudo hoy?", style = TitleStyle.copy(fontSize = 24.sp), textAlign = TextAlign.Center)

            Text(
                text = "Escribe tu pregunta o pega código. Configura tu API key en Ajustes para empezar.",
                style = BodyStyle.copy(color = AppTextMuted, fontSize = 15.sp, lineHeight = 22.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.86f)
            )
        }
    }
}
