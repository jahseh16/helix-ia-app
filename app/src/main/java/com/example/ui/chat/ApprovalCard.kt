package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.runtime.ApprovalDecision
import com.example.agent.runtime.ApprovalRequest
import com.example.ui.theme.*
import org.json.JSONObject

/** Petición de aprobación visible sobre la píldora: el agente está bloqueado hasta que el usuario decida. */
@Composable
fun ApprovalCard(
    request: ApprovalRequest,
    onDecision: (ApprovalDecision) -> Unit,
    modifier: Modifier = Modifier
) {
    val args = remember(request.call.id) {
        runCatching { JSONObject(request.call.argumentsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurface2,
        border = BorderStroke(1.dp, AppWarning.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .testTag("approval_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = AppWarning, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "El agente pide permiso",
                    style = BodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppWarning)
                )
            }
            Text(
                text = ToolPresentation.title(request.call.name),
                style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
            Surface(shape = RoundedCornerShape(12.dp), color = AppCodeBg) {
                Text(
                    text = ToolPresentation.subtitle(request.call.name, args).ifBlank { request.summary },
                    style = BodyStyle.copy(fontFamily = UiMono, fontSize = 12.sp, color = AppText),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DecisionButton("Denegar", AppDanger, outlined = true, modifier = Modifier.weight(1f).testTag("deny_button")) {
                    onDecision(ApprovalDecision.DENY)
                }
                DecisionButton("Permitir", AppAccent, outlined = false, modifier = Modifier.weight(1f).testTag("allow_button")) {
                    onDecision(ApprovalDecision.ALLOW_ONCE)
                }
            }
            if (request.canRemember) {
                TextButton(
                    onClick = { onDecision(ApprovalDecision.ALLOW_SESSION) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("allow_session_button")
                ) {
                    Text("Permitir siempre en esta sesión", color = AppAccentSoft, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DecisionButton(label: String, color: Color, outlined: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (outlined) Color.Transparent else color,
        border = BorderStroke(1.dp, color),
        modifier = modifier.height(46.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = BodyStyle.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (outlined) color else Color.White
                )
            )
        }
    }
}
