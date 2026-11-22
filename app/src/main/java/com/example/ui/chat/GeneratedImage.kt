package com.example.ui.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Imagen generada por la herramienta `generate_image`, con opciones de ver a pantalla completa y guardar. */
@Composable
fun GeneratedImage(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeScaled(File(path), 1600) }
    }
    var fullscreen by remember { mutableStateOf(false) }
    var saveMessage by remember(path) { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, AppStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Imagen generada",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clickable { fullscreen = true }
                        .testTag("generated_image")
                )
            } else {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (File(path).isFile) "Cargando imagen…" else "La imagen ya no está disponible",
                        style = MetaStyle
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ImageAction("Ver", Icons.Default.Fullscreen, "view_image_button") { if (bitmap != null) fullscreen = true }
            ImageAction("Guardar", Icons.Default.Download, "save_image_button") {
                saveMessage = if (saveToGallery(context, File(path))) "Guardada en Imágenes/Helix" else "No se pudo guardar la imagen"
            }
            saveMessage?.let { Text(it, style = MetaStyle.copy(fontSize = 12.sp, color = AppAccentSoft)) }
        }
    }

    val bmp = bitmap
    if (fullscreen && bmp != null) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable { fullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Imagen generada a pantalla completa", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ImageAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = AppSurface3.copy(alpha = 0.7f),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).testTag(tag)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AppAccentSoft, modifier = Modifier.size(16.dp))
            Text(" $label", style = MetaStyle.copy(color = AppAccentSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

private fun decodeScaled(file: File, maxSide: Int): Bitmap? = runCatching {
    if (!file.isFile) return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** Copia la imagen a la galería (Pictures/Helix). Android 10+ usa MediaStore, sin permisos extra. */
private fun saveToGallery(context: Context, source: File): Boolean = runCatching {
    if (!source.isFile) return@runCatching false
    val mime = when (source.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Helix")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return@runCatching false
        true
    } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Helix").also { it.mkdirs() }
        source.copyTo(File(dir, source.name), overwrite = true)
        true
    }
}.getOrDefault(false)
