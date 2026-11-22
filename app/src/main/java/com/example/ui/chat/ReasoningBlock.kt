package com.example.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/** Razonamiento del modelo, plegado por defecto. */
@Composable
fun ReasoningBlock(text: String, live: Boolean, key: Any, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "reasoning_chevron")

    Column(modifier = modifier.fillMaxWidth().padding(end = 20.dp).animateContentSize()) {
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = AppAccentSoft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (live) "Razonando…" else "Razonamiento",
                style = MetaStyle.copy(color = AppAccentSoft, fontSize = 13.sp)
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Ocultar razonamiento" else "Ver razonamiento",
                tint = AppTextMuted,
                modifier = Modifier.padding(start = 2.dp).size(18.dp).rotate(chevron)
            )
        }
        if (expanded) {
            Text(
                text = text.trim(),
                style = BodyStyle.copy(fontSize = 13.sp, lineHeight = 19.sp, color = AppTextMuted, fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 6.dp)
            )
        }
    }
}
