package com.example.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ConversationEntity
import com.example.data.profile.UserProfile
import com.example.ui.components.GlassIconButton
import com.example.ui.components.HelixLogo
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*
import java.util.Calendar

/**
 * Panel lateral estilo app de IA: "Nuevo chat", historial agrupado por fecha (tocar para abrir,
 * ⋮ para borrar) y, abajo, el área del usuario con acceso a su perfil y a Ajustes.
 */
@Composable
fun ChatDrawer(
    conversations: List<ConversationEntity>,
    currentId: Long,
    profile: UserProfile,
    onNewChat: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAll: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val groups = remember(conversations) { groupByDate(conversations) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)) {
            HelixLogo(size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text("Helix", style = TitleStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
        }

        // Nuevo chat (acento azul: acción principal del panel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AppAccent)
                .clickable(onClick = onNewChat)
                .padding(horizontal = 16.dp, vertical = 13.dp)
                .testTag("drawer_new_chat"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Nuevo chat", style = BodyStyle.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
        }

        Spacer(Modifier.height(16.dp))

        if (conversations.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    "Aún no hay conversaciones.\nLas que empieces aparecerán aquí.",
                    style = MetaStyle.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                groups.forEach { (label, items) ->
                    item(key = "h-$label") {
                        Text(
                            label.uppercase(),
                            style = MetaStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp)
                        )
                    }
                    items(items, key = { it.id }) { c ->
                        ConversationRow(
                            conversation = c,
                            selected = c.id == currentId,
                            onClick = { onSelect(c.id) },
                            onDelete = { confirmDelete = c }
                        )
                    }
                }
                item(key = "delete-all") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { confirmDeleteAll = true }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("drawer_delete_all"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null, tint = AppDanger.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Borrar todo el historial", style = MetaStyle.copy(color = AppDanger.copy(alpha = 0.85f), fontSize = 13.sp))
                    }
                }
            }
        }

        // Área del usuario: perfil + Ajustes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassFill)
                .border(1.dp, AppStroke, RoundedCornerShape(20.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenProfile)
                    .padding(4.dp)
                    .testTag("drawer_profile"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(profile.photoPath, profile.initials, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, style = BodyStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (profile.username.isNotBlank()) "@${profile.username}" else "Editar perfil",
                        style = MetaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GlassIconButton(Icons.Default.Settings, "Ajustes", AppText, onOpenSettings, Modifier.testTag("settings_button"))
        }
    }

    confirmDelete?.let { c ->
        ConfirmDialog(
            title = "¿Borrar este chat?",
            text = "«${c.title}» se eliminará del historial. No se puede deshacer.",
            confirm = "Borrar",
            onConfirm = { onDelete(c.id); confirmDelete = null },
            onDismiss = { confirmDelete = null }
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "¿Borrar todo el historial?",
            text = "Se eliminarán todas las conversaciones. No se puede deshacer.",
            confirm = "Borrar todo",
            onConfirm = { onDeleteAll(); confirmDeleteAll = false },
            onDismiss = { confirmDeleteAll = false }
        )
    }
}

@Composable
private fun ConversationRow(conversation: ConversationEntity, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) GlassHighlight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ChatBubbleOutline, null,
            tint = if (selected) AppAccentSoft else AppTextMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            conversation.title,
            style = BodyStyle.copy(fontSize = 14.5.sp, color = if (selected) AppText else AppText.copy(alpha = 0.85f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { menu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MoreVert, "Opciones del chat", tint = AppTextMuted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = AppSurface3, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text("Borrar chat", color = AppDanger) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = AppDanger) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppSurface3,
        shape = ShapeCard,
        title = { Text(title, color = AppText, fontWeight = FontWeight.SemiBold) },
        text = { Text(text, color = AppTextMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = AppDanger, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = AppAccent) } }
    )
}

/** Agrupa por "Hoy", "Ayer", "Últimos 7 días" y "Anteriores" (lista ya ordenada por actividad reciente). */
internal fun groupByDate(conversations: List<ConversationEntity>, now: Long = System.currentTimeMillis()): List<Pair<String, List<ConversationEntity>>> {
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val day = 24L * 60 * 60 * 1000
    return conversations.groupBy {
        when {
            it.updatedAt >= startOfToday -> "Hoy"
            it.updatedAt >= startOfToday - day -> "Ayer"
            it.updatedAt >= startOfToday - 7 * day -> "Últimos 7 días"
            else -> "Anteriores"
        }
    }.toList()
}
