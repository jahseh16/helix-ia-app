package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ModelCatalog
import com.example.ui.chat.BrandLogo
import com.example.ui.theme.AppAccentSoft
import com.example.ui.theme.AppStroke
import com.example.ui.theme.AppText
import com.example.ui.theme.AppTextMuted
import com.example.ui.theme.GlassFill
import com.example.ui.theme.MetaStyle
import com.example.ui.theme.TitleStyle

/** Barra superior: menú (historial) · logo + "Helix" + modelo activo · nuevo chat. */
@Composable
fun HelixTopBar(
    provider: String,
    modelId: String,
    onOpenMenu: () -> Unit,
    onNewChat: () -> Unit
) {
    val model = remember(provider, modelId) { ModelCatalog.resolve(provider, modelId) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(Icons.Default.Menu, "Abrir historial de chats", AppText, onOpenMenu, Modifier.testTag("menu_button"))
        Spacer(Modifier.width(12.dp))
        HelixLogo(size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Helix", style = TitleStyle.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(model.brand, size = 15.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    model.name,
                    style = MetaStyle.copy(color = AppAccentSoft),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(Icons.Outlined.EditNote, "Nuevo chat", AppText, onNewChat, Modifier.testTag("new_chat_button"))
    }
}

/** Botón circular de cristal (relleno translúcido + filo blanco), objetivo táctil de 44 dp. */
@Composable
fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = AppTextMuted,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, AppStroke, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
