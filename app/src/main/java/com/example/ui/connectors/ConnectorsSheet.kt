package com.example.ui.connectors

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.components.copyToClipboard
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.connectors.Connector
import com.example.connectors.ConnectorState
import com.example.ui.Brand
import com.example.ui.chat.BrandLogo
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*

private fun brandFor(iconKey: String): Brand = when (iconKey) {
    "github" -> Brand.GITHUB
    else -> Brand.GENERIC
}

/** Hoja "Conectores": una tarjeta glass por servicio con su estado y el botón Conectar / Desconectar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsSheet(
    connectors: List<Connector>,
    onConnect: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onDismiss: () -> Unit,
    backgroundUnrestricted: Boolean = true,
    onRequestBackground: () -> Unit = {}
) {
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
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Text("Conectores", style = TitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Text("Conecta servicios para que el agente trabaje con ellos", style = MetaStyle)
            }
            connectors.forEach { c ->
                ConnectorCard(c, onConnect = { onConnect(c.id) }, onDisconnect = { onDisconnect(c.id) })
                ConnectDialogs(c, onCancel = { onCancel(c.id) }, backgroundUnrestricted, onRequestBackground)
            }
            BackgroundCard(backgroundUnrestricted, onRequestBackground)
        }
    }
}

@Composable
private fun ConnectorCard(connector: Connector, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val state by connector.state.collectAsStateWithLifecycle()
    val connected = state as? ConnectorState.Connected

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurface2,
        border = BorderStroke(1.dp, if (connected != null) AppGreen.copy(alpha = 0.45f) else AppStroke),
        modifier = Modifier.fillMaxWidth().testTag("connector_${connector.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(brandFor(connector.iconKey), size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(connector.title, style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                    StatusLine(state)
                }
            }
            Text(connector.description, style = MetaStyle.copy(fontSize = 13.sp, lineHeight = 18.sp))

            if (connected != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassFill, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    UserAvatar(connected.account.avatarPath, connected.account.login.take(2).uppercase(), 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        connected.account.displayName?.let { Text(it, style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)) }
                        Text("@${connected.account.login}", style = MetaStyle)
                    }
                }
            }
            (state as? ConnectorState.Failed)?.let {
                Text(it.message, style = MetaStyle.copy(color = AppDanger, fontSize = 13.sp, lineHeight = 18.sp))
            }

            when (state) {
                is ConnectorState.Connected -> OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AppDanger.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("disconnect_${connector.id}")
                ) { Text("Desconectar", color = AppDanger, fontWeight = FontWeight.SemiBold) }

                is ConnectorState.Connecting -> Button(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppTextMuted)
                    Spacer(Modifier.width(10.dp))
                    Text("Conectando…")
                }

                else -> Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("connect_${connector.id}")
                ) { Text("Conectar", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun StatusLine(state: ConnectorState) {
    val (label, color) = when (state) {
        is ConnectorState.Connected -> "Conectado" to AppGreen
        is ConnectorState.Connecting -> "Esperando autorización" to AppWarning
        is ConnectorState.Failed -> "Error al conectar" to AppDanger
        ConnectorState.Disconnected -> "Desconectado" to AppTextMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(label, style = MetaStyle.copy(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

/** Diálogo del código de dispositivo: se muestra mientras hay una autorización pendiente. */
@Composable
private fun ConnectDialogs(connector: Connector, onCancel: () -> Unit, backgroundUnrestricted: Boolean, onRequestBackground: () -> Unit) {
    val state by connector.state.collectAsStateWithLifecycle()
    val connecting = state as? ConnectorState.Connecting ?: return
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2200); copied = false } }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = AppSurface3,
        shape = ShapeCard,
        title = { Text("Conectar ${connector.title}", color = AppText, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (connecting.userCode == null) {
                    CircularProgressIndicator(color = AppAccent, modifier = Modifier.padding(16.dp))
                    Text("Pidiendo el código a ${connector.title}…", color = AppTextMuted, textAlign = TextAlign.Center)
                } else {
                    Text("Tu código", style = MetaStyle)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = AppBg,
                        border = BorderStroke(1.dp, AppAccent.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(vertical = 10.dp).testTag("device_user_code")
                    ) {
                        Text(
                            connecting.userCode,
                            style = TitleStyle.copy(fontSize = 32.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold, color = AppText),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                    // Copiar explícito: escribe en el portapapeles y confirma con cambio de estado y aviso
                    OutlinedButton(
                        onClick = {
                            val ok = copyToClipboard(context, "GitHub Code", connecting.userCode)
                            copied = ok
                            if (!ok) android.widget.Toast.makeText(context, "No se pudo copiar el código", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (copied) AppGreen else AppStroke),
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("copy_code_button")
                    ) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, tint = if (copied) AppGreen else AppAccentSoft, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (copied) "¡Copiado!" else "Copiar código", color = if (copied) AppGreen else AppAccentSoft, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Abre GitHub, pega el código y pulsa «Authorize». Vuelve aquí: la conexión se completa sola.",
                        color = AppTextMuted, textAlign = TextAlign.Center, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            copyToClipboard(context, "GitHub Code", connecting.userCode)
                            openInBrowser(context, connecting.verificationUri ?: "https://github.com/login/device")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("open_verification")
                    ) {
                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir GitHub para autorizar", fontWeight = FontWeight.SemiBold)
                    }
                    if (!backgroundUnrestricted) {
                        TextButton(onClick = onRequestBackground, modifier = Modifier.testTag("dialog_background_hint")) {
                            Text("Permitir segundo plano para no perder la conexión", color = AppWarning, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppTextMuted)
                        Spacer(Modifier.width(8.dp))
                        Text("Esperando tu autorización…", style = MetaStyle)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar", color = AppTextMuted) } }
    )
}

/** Estado del permiso de trabajar sin restricciones de batería, con botón para pedirlo. */
@Composable
private fun BackgroundCard(unrestricted: Boolean, onRequest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppSurface2,
        border = BorderStroke(1.dp, if (unrestricted) AppGreen.copy(alpha = 0.45f) else AppStroke),
        modifier = Modifier.fillMaxWidth().testTag("background_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryChargingFull, null, tint = if (unrestricted) AppGreen else AppWarning, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trabajo en segundo plano", style = BodyStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                    Text(
                        if (unrestricted) "Sin restricciones de batería" else "Restringido por el ahorro de batería",
                        style = MetaStyle.copy(color = if (unrestricted) AppGreen else AppWarning)
                    )
                }
            }
            Text(
                "Permite que Helix siga activo al ir al navegador a autorizar o al ejecutar tareas largas, sin que Android lo congele.",
                style = MetaStyle.copy(fontSize = 13.sp, lineHeight = 18.sp)
            )
            if (!unrestricted) {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("request_background")
                ) { Text("Permitir sin restricciones", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
