package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.permissions.AgentMode
import com.example.ui.theme.*

private data class ModeStyle(val label: String, val title: String, val description: String, val icon: ImageVector, val color: Color)

private fun styleOf(mode: AgentMode) = when (mode) {
    AgentMode.AUTOMATIC -> ModeStyle(
        label = "Auto",
        title = "Automático",
        description = "Edita archivos y consulta la web sin preguntar. Los comandos de shell siempre piden confirmación.",
        icon = Icons.Default.Bolt,
        color = AppWarning
    )
    AgentMode.ASK -> ModeStyle(
        label = "Pedir",
        title = "Pedir permiso",
        description = "Pide confirmación antes de modificar archivos, usar la red o ejecutar comandos.",
        icon = Icons.Default.Shield,
        color = AppAccentSoft
    )
}

/** Selector compacto del modo del agente, junto al botón de enviar. Cambia al instante. */
@Composable
fun AgentModeSelector(mode: AgentMode, onModeChange: (AgentMode) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = styleOf(mode)

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = current.color.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, current.color.copy(alpha = 0.35f)),
            modifier = Modifier
                .height(36.dp)
                .clickable { open = true }
                .testTag("mode_selector")
                .semantics { contentDescription = "Modo del agente: ${current.title}" }
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(current.icon, contentDescription = null, tint = current.color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(current.label, style = MetaStyle.copy(color = current.color, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(AppSurface3, RoundedCornerShape(18.dp)).widthIn(min = 260.dp, max = 300.dp)
        ) {
            AgentMode.entries.forEach { m ->
                val st = styleOf(m)
                Row(
                    modifier = Modifier
                        .clickable {
                            onModeChange(m)
                            open = false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("mode_option_${m.name}"),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(st.icon, contentDescription = null, tint = st.color, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(st.title, style = BodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (m == mode) st.color else AppText))
                        Text(st.description, style = MetaStyle.copy(fontSize = 12.sp, lineHeight = 17.sp))
                    }
                    if (m == mode) Icon(Icons.Default.Check, "Activo", tint = st.color, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                }
            }
        }
    }
}
