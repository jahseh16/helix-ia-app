package com.example.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.profile.UserProfile
import com.example.ui.components.HelixLogo
import com.example.ui.components.StarfieldBackground
import com.example.ui.components.UserAvatar
import com.example.ui.settings.helixFieldColors
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Primera apertura: pantalla completa para crear el perfil (todo opcional, con "Omitir por ahora").
 */
@Composable
fun ProfileSetupScreen(onSave: (name: String, username: String, email: String, photo: Uri?) -> Unit, onSkip: () -> Unit) {
    StarfieldBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HelixLogo(size = 72.dp, corner = 22.dp)
            Spacer(Modifier.height(16.dp))
            Text("Bienvenido a Helix", style = TitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Crea tu perfil. Se guarda solo en este teléfono y puedes cambiarlo cuando quieras.",
                style = MetaStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            ProfileForm(
                initial = UserProfile(),
                primaryLabel = "Continuar",
                onSave = { n, u, e, photo, _ -> onSave(n, u, e, photo) }
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip, modifier = Modifier.testTag("profile_skip")) {
                Text("Omitir por ahora", color = AppTextMuted, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Formulario de perfil: foto (selector de fotos del sistema, sin permisos), nombre, usuario y correo.
 * Se usa en la pantalla inicial y en la hoja "Editar perfil".
 */
@Composable
fun ProfileForm(
    initial: UserProfile,
    primaryLabel: String,
    onSave: (name: String, username: String, email: String, photo: Uri?, removePhoto: Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var username by rememberSaveable { mutableStateOf(initial.username) }
    var email by rememberSaveable { mutableStateOf(initial.email) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var removePhoto by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photo = uri.toString(); removePhoto = false }
    }
    val preview by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { s ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(s)
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    val usernameError = UserProfile.usernameError(username)
    val emailError = UserProfile.emailError(email)
    val draft = UserProfile(name, username, email)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.clickable {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.testTag("profile_photo")) {
            UserAvatar(
                photoPath = if (removePhoto) null else initial.photoPath,
                initials = draft.initials,
                size = 104.dp,
                image = preview
            )
            Box(
                modifier = Modifier.size(34.dp).background(AppAccent, CircleShape).border(2.dp, Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PhotoCamera, "Elegir foto", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
        if ((initial.photoPath != null && !removePhoto) || photo != null) {
            TextButton(onClick = { photo = null; removePhoto = true }) { Text("Quitar foto", color = AppTextMuted, fontSize = 13.sp) }
        } else {
            Text("Toca para elegir tu foto", style = MetaStyle)
        }

        Field("Nombre", name, { name = it }, "profile_name", KeyboardType.Text)
        Field("Nombre de usuario", username, { username = it.replace(" ", "") }, "profile_username", KeyboardType.Ascii, prefix = "@", error = usernameError)
        Field("Correo", email, { email = it.trim() }, "profile_email", KeyboardType.Email, error = emailError, last = true)

        Button(
            onClick = { onSave(name, username, email, photo?.let(Uri::parse), removePhoto) },
            enabled = usernameError == null && emailError == null,
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.White, disabledContainerColor = AppSurface3),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("profile_save")
        ) { Text(primaryLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    tag: String,
    keyboard: KeyboardType,
    prefix: String? = null,
    error: String? = null,
    last: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MetaStyle) },
        prefix = prefix?.let { p -> { Text(p, color = AppTextMuted) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e, color = AppDanger, fontSize = 12.sp) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        textStyle = BodyStyle,
        colors = helixFieldColors(),
        shape = ShapeMedium,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}
