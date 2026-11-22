package com.example.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.AuthUser
import com.example.cloud.AuthValidation
import com.example.cloud.SyncState
import com.example.ui.AuthMessage
import com.example.ui.components.UserAvatar
import com.example.ui.settings.helixFieldColors
import com.example.ui.theme.*

/**
 * Cuenta de Helix (Firebase): inicio de sesión / registro con correo y Google, y —con sesión iniciada—
 * el usuario conectado, el estado de la sincronización y el cierre de sesión. Estética ultra-negra glass.
 */
@Composable
fun AccountPanel(
    user: AuthUser?,
    sync: SyncState,
    busy: Boolean,
    message: AuthMessage?,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onGoogle: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onClearMessage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("Cuenta", style = TitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold))
            Text(
                if (user == null) "Inicia sesión para guardar y recuperar tus chats en la nube" else "Tus chats se guardan en tu cuenta",
                style = MetaStyle
            )
        }

        if (user == null) SignedOut(busy, message, onSignIn, onRegister, onResetPassword, onGoogle, onClearMessage)
        else SignedIn(user, sync, busy, message, onSyncNow, onSignOut)
    }
}

@Composable
private fun ColumnScope.SignedOut(
    busy: Boolean,
    message: AuthMessage?,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onReset: (String) -> Unit,
    onGoogle: () -> Unit,
    onClearMessage: () -> Unit
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val emailError = if (submitted) AuthValidation.emailError(email) else null
    val passwordError = if (submitted) AuthValidation.passwordError(password) else null

    // Selector segmentado
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppSurface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("Iniciar sesión" to false, "Crear cuenta" to true).forEach { (label, register) ->
            val selected = registerMode == register
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) AppAccent else Color.Transparent)
                    .clickable { registerMode = register; submitted = false; onClearMessage() }
                    .padding(vertical = 12.dp)
                    .testTag(if (register) "account_tab_register" else "account_tab_signin"),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = BodyStyle.copy(fontSize = 14.sp, color = if (selected) Color.White else AppTextMuted, fontWeight = FontWeight.SemiBold))
            }
        }
    }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it.trim() },
        label = { Text("Correo", style = MetaStyle) },
        singleLine = true,
        isError = emailError != null,
        supportingText = emailError?.let { e -> { Text(e, color = AppDanger, fontSize = 12.sp) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        textStyle = BodyStyle,
        colors = helixFieldColors(),
        shape = ShapeMedium,
        modifier = Modifier.fillMaxWidth().testTag("account_email")
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Contraseña", style = MetaStyle) },
        singleLine = true,
        isError = passwordError != null,
        supportingText = passwordError?.let { e -> { Text(e, color = AppDanger, fontSize = 12.sp) } },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Mostrar u ocultar", tint = AppTextMuted)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        textStyle = BodyStyle,
        colors = helixFieldColors(),
        shape = ShapeMedium,
        modifier = Modifier.fillMaxWidth().testTag("account_password")
    )

    message?.let { MessageBanner(it) }

    Button(
        onClick = {
            submitted = true
            if (AuthValidation.emailError(email) == null && AuthValidation.passwordError(password) == null) {
                if (registerMode) onRegister(email, password) else onSignIn(email, password)
            }
        },
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.White, disabledContainerColor = AppSurface3),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("account_submit")
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Text(if (registerMode) "Crear cuenta" else "Iniciar sesión", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    if (!registerMode) {
        TextButton(
            onClick = {
                val err = AuthValidation.emailError(email)
                if (err != null) submitted = true else onReset(email)
            },
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("account_reset")
        ) { Text("¿Olvidaste tu contraseña?", color = AppAccentSoft, fontSize = 14.sp) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(AppStroke))
        Text("  o  ", style = MetaStyle)
        Box(Modifier.weight(1f).height(1.dp).background(AppStroke))
    }

    OutlinedButton(
        onClick = onGoogle,
        enabled = !busy,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppStroke),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassFill, contentColor = AppText),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("account_google")
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) { Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Spacer(Modifier.width(12.dp))
        Text("Continuar con Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SignedIn(
    user: AuthUser,
    sync: SyncState,
    busy: Boolean,
    message: AuthMessage?,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(GlassFill)
            .border(1.dp, AppStroke, RoundedCornerShape(22.dp))
            .padding(16.dp)
            .testTag("account_user_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(null, initialsOf(user.label), 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.label, style = BodyStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            user.email?.takeIf { it != user.label }?.let { Text(it, style = MetaStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(if (user.usesGoogle) "Conectado con Google" else "Conectado con correo", style = MetaStyle.copy(color = AppGreen))
        }
    }

    // Estado de la sincronización
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassFill)
            .border(1.dp, AppStroke, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            sync.syncing -> CircularProgressIndicator(color = AppAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            sync.error != null -> Icon(Icons.Default.CloudOff, null, tint = AppWarning, modifier = Modifier.size(22.dp))
            else -> Icon(Icons.Default.CloudDone, null, tint = AppGreen, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when {
                    sync.syncing -> "Sincronizando…"
                    sync.error != null -> "No se pudo sincronizar"
                    sync.lastSyncAt != null -> "Chats sincronizados"
                    else -> "Listo para sincronizar"
                },
                style = BodyStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                sync.error ?: "Chats, mensajes e imágenes se guardan en Firebase y se recuperan al iniciar sesión.",
                style = MetaStyle.copy(color = if (sync.error != null) AppWarning else AppTextMuted)
            )
        }
        IconButton(onClick = onSyncNow, enabled = !sync.syncing, modifier = Modifier.testTag("account_sync")) {
            Icon(Icons.Default.Sync, "Sincronizar ahora", tint = AppAccentSoft)
        }
    }

    message?.let { MessageBanner(it) }

    OutlinedButton(
        onClick = { confirm = true },
        enabled = !busy,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AppDanger.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppDanger),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("account_signout")
    ) { Text("Cerrar sesión", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = AppSurface2,
            shape = ShapeCard,
            title = { Text("¿Cerrar sesión?", color = AppText, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Tus chats seguirán guardados en tu cuenta. Se quitarán de este teléfono y volverán al iniciar sesión.",
                    color = AppTextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; onSignOut() }, modifier = Modifier.testTag("account_signout_confirm")) {
                    Text("Cerrar sesión", color = AppDanger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar", color = AppTextMuted) } }
        )
    }
}

@Composable
private fun MessageBanner(message: AuthMessage) {
    val color = if (message.isError) AppDanger else AppGreen
    Text(
        text = message.text,
        style = MetaStyle.copy(color = color, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("account_message")
    )
}

private fun initialsOf(label: String): String =
    label.substringBefore('@').split(' ', '.', '_', '-').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "H" }
