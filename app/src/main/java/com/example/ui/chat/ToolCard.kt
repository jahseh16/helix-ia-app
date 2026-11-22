package com.example.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.model.ToolCall
import com.example.data.database.ChatMessageEntity
import com.example.ui.LiveToolState
import com.example.ui.theme.*
import org.json.JSONObject

enum class ToolCardStatus(val label: String) {
    PENDING("En cola"),
    AWAITING("Esperando aprobación"),
    RUNNING("Ejecutando"),
    OK("Completada"),
    ERROR("Error"),
    DENIED("Rechazada"),
    CANCELLED("Cancelada")
}

private fun ToolCardStatus.color(): Color = when (this) {
    ToolCardStatus.PENDING, ToolCardStatus.CANCELLED -> AppTextMuted
    ToolCardStatus.AWAITING -> AppWarning
    ToolCardStatus.RUNNING -> AppAccent
    ToolCardStatus.OK -> AppGreen
    ToolCardStatus.ERROR -> AppDanger
    ToolCardStatus.DENIED -> AppWarning
}

/** Tarjeta desplegable de una llamada a herramienta: estado en vivo, argumentos y resultado. */
@Composable
fun ToolCard(
    call: ToolCall,
    result: ChatMessageEntity?,
    live: LiveToolState?,
    agentBusy: Boolean,
    modifier: Modifier = Modifier
) {
    val args = remember(call.argumentsJson) {
        runCatching { JSONObject(call.argumentsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
    }
    val status = when {
        result != null -> when (result.toolStatus) {
            "OK" -> ToolCardStatus.OK
            "DENIED" -> ToolCardStatus.DENIED
            else -> ToolCardStatus.ERROR
        }
        live == LiveToolState.AWAITING_APPROVAL -> ToolCardStatus.AWAITING
        live == LiveToolState.RUNNING -> ToolCardStatus.RUNNING
        agentBusy -> ToolCardStatus.PENDING
        else -> ToolCardStatus.CANCELLED
    }
    val color = status.color()

    var expanded by rememberSaveable(call.id) { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status == ToolCardStatus.AWAITING) expanded = true
    }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, if (status == ToolCardStatus.RUNNING || status == ToolCardStatus.AWAITING) color.copy(alpha = 0.55f) else AppStroke),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ToolPresentation.icon(call.name), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ToolPresentation.title(call.name),
                        style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = ToolPresentation.subtitle(call.name, args),
                        style = MetaStyle.copy(fontFamily = UiMono, fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(status, color)
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = AppTextMuted,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(20.dp)
                        .rotate(chevron)
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolArguments(call.name, args)
                    if (result != null) {
                        SectionLabel("Resultado")
                        OutputBox(
                            text = result.text.ifBlank { "(sin salida)" },
                            tint = if (result.toolStatus == "OK") AppText else color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ToolCardStatus, color: Color) {
    if (status == ToolCardStatus.RUNNING) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = color,
            strokeWidth = 2.dp
        )
    } else {
        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
            Text(
                text = status.label,
                style = MetaStyle.copy(color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text.uppercase(), style = MetaStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
}

@Composable
private fun ToolArguments(name: String, args: JSONObject) {
    when (name) {
        "edit_file" -> {
            SectionLabel("Cambio")
            DiffBlock(old = args.optString("old_string"), new = args.optString("new_string"))
        }
        "write_file" -> {
            SectionLabel("Contenido")
            OutputBox(text = previewLines(args.optString("content"), 30), tint = AppText)
        }
        "run_shell" -> {
            SectionLabel("Comando")
            OutputBox(text = "$ " + args.optString("command"), tint = AppCyan)
        }
        else -> {
            val lines = args.keys().asSequence().map { k -> "$k: ${args.opt(k)}" }.joinToString("\n")
            if (lines.isNotBlank()) {
                SectionLabel("Argumentos")
                OutputBox(text = lines.take(1500), tint = AppTextMuted)
            }
        }
    }
}

private fun previewLines(text: String, max: Int): String {
    val lines = text.lines()
    return if (lines.size <= max) text else lines.take(max).joinToString("\n") + "\n… (${lines.size - max} líneas más)"
}

@Composable
private fun OutputBox(text: String, tint: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = AppCodeBg, border = BorderStroke(1.dp, AppStroke)) {
        Text(
            text = if (text.length > 4000) text.take(4000) + "\n… (recortado en pantalla)" else text,
            style = BodyStyle.copy(fontFamily = UiMono, fontSize = 12.sp, lineHeight = 17.sp, color = tint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        )
    }
}

@Composable
private fun DiffBlock(old: String, new: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = AppCodeBg, border = BorderStroke(1.dp, AppStroke)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            old.lines().take(40).forEach { DiffLine("- $it", AppDanger) }
            new.lines().take(40).forEach { DiffLine("+ $it", AppGreen) }
        }
    }
}

@Composable
private fun DiffLine(text: String, color: Color) {
    Text(
        text = text,
        style = BodyStyle.copy(fontFamily = UiMono, fontSize = 12.sp, lineHeight = 17.sp, color = color),
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 1.dp)
    )
}
