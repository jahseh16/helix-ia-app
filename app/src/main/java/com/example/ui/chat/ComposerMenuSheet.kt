package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.AiModel
import com.example.ui.ModelCatalog
import com.example.ui.theme.*

/**
 * Hoja compacta del botón "+": modelo y modo agente. Sustituye a la antigua fila de chips
 * para que el campo de escritura sea una sola píldora limpia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerMenuSheet(
    provider: String,
    modelId: String,
    agentEnabled: Boolean,
    onSelectModel: (provider: String, modelId: String) -> Unit,
    onToggleAgent: () -> Unit,
    onOpenConnectors: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var modelsOpen by remember { mutableStateOf(false) }
    val current = remember(provider, modelId) { ModelCatalog.resolve(provider, modelId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppSurface,
        shape = ShapeSheet,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppStroke) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Opciones", style = TitleStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 4.dp))

            // --- Modelo
            Group {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { modelsOpen = !modelsOpen }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("menu_model_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandLogo(current.brand, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modelo", style = MetaStyle)
                        Text(current.name, style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ExpandMore, null, tint = AppTextMuted)
                }
                AnimatedVisibility(visible = modelsOpen) {
                    Column(modifier = Modifier.animateContentSize()) {
                        ModelCatalog.providers.forEach { p ->
                            Divider()
                            Text(
                                text = p.uppercase(),
                                style = MetaStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                            ModelCatalog.modelsFor(p).forEach { m ->
                                ModelRow(
                                    model = m,
                                    selected = p == provider && m.id == modelId,
                                    onClick = {
                                        onSelectModel(p, m.id)
                                        modelsOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // --- Conectores
            Group {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenConnectors() }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("menu_connectors_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.Extension, AppCyan)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Conectores", style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        Text("GitHub y más: referencia tus repos con @ o /", style = MetaStyle)
                    }
                }
            }

            // --- Modo agente
            Group {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAgent() }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("menu_agent_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Default.AutoAwesome, AppAccent)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo agente", style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        Text(
                            if (agentEnabled) "Puede leer, crear y editar archivos, ejecutar comandos y consultar la web"
                            else "Solo conversa, sin usar herramientas",
                            style = MetaStyle
                        )
                    }
                    Switch(
                        checked = agentEnabled,
                        onCheckedChange = { onToggleAgent() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = AppAccent,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = AppSurface3,
                            uncheckedThumbColor = AppTextMuted,
                            uncheckedBorderColor = AppStroke
                        )
                    )
                }
                if (agentEnabled && current.tools == false) {
                    Text(
                        "Este modelo no admite herramientas: responderá solo como chat.",
                        style = MetaStyle.copy(color = AppWarning),
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AppSurface2, border = BorderStroke(1.dp, AppStroke)) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppStroke))
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ModelRow(model: AiModel, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLogo(model.brand, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.name,
                style = BodyStyle.copy(fontSize = 15.sp, color = if (selected) AppAccentSoft else AppText, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when (model.tools) {
                true -> Text("Herramientas", style = MetaStyle.copy(color = AppGreen, fontSize = 11.sp))
                false -> Text("Solo chat", style = MetaStyle.copy(fontSize = 11.sp))
                null -> Unit
            }
        }
        if (selected) Icon(Icons.Default.Check, "Seleccionado", tint = AppAccent, modifier = Modifier.size(20.dp))
    }
}
