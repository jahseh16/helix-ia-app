package com.example.ui.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/**
 * Único diálogo de permisos de almacenamiento: se muestra al iniciar por primera vez y de nuevo si el agente
 * intenta usar archivos sin el permiso. "Permitir" abre los ajustes de Android "Acceso a todos los archivos".
 */
@Composable
fun StorageAccessDialog(onAllow: () -> Unit, onDeny: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDeny,
        containerColor = AppSurface2,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "¿Permitir que Helix acceda a todos los archivos de este dispositivo?",
                color = AppText,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                "Así el agente podrá leer, crear y modificar archivos en tu almacenamiento (por ejemplo " +
                    "/sdcard/Download), como un gestor de archivos. Se abrirán los ajustes de Android: " +
                    "activa el interruptor para Helix y vuelve a la app.",
                color = AppTextMuted
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow, modifier = Modifier.testTag("storage_allow")) {
                Text("Permitir", color = AppAccent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny, modifier = Modifier.testTag("storage_deny")) {
                Text("Denegar", color = AppTextMuted)
            }
        }
    )
}

/** Android 10 no permite acceso total al almacenamiento: se avisa de que se usa la carpeta interna. */
@Composable
fun StorageUnsupportedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppSurface2,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Almacenamiento limitado en Android 10", color = AppText, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Android 10 no permite que Helix acceda a todos los archivos del dispositivo. " +
                    "El agente trabajará en una carpeta interna y privada de la app.",
                color = AppTextMuted
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("storage_notice_ok")) {
                Text("Entendido", color = AppAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
