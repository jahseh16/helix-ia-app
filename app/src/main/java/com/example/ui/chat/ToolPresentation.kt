package com.example.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONObject

/** Nombre, icono y resumen legibles de cada herramienta (solo presentación). */
object ToolPresentation {

    fun title(name: String): String = when (name) {
        "read_file" -> "Leer archivo"
        "write_file" -> "Escribir archivo"
        "edit_file" -> "Editar archivo"
        "list_dir" -> "Listar carpeta"
        "glob" -> "Buscar archivos"
        "grep" -> "Buscar en archivos"
        "http_request" -> "Petición HTTP"
        "web_fetch" -> "Leer página web"
        "run_shell" -> "Ejecutar comando"
        else -> name
    }

    fun icon(name: String): ImageVector = when (name) {
        "read_file" -> Icons.Default.Description
        "write_file" -> Icons.AutoMirrored.Filled.NoteAdd
        "edit_file" -> Icons.Default.Edit
        "list_dir" -> Icons.Default.FolderOpen
        "glob" -> Icons.Default.FindInPage
        "grep" -> Icons.Default.Search
        "http_request" -> Icons.Default.Http
        "web_fetch" -> Icons.Default.Language
        "run_shell" -> Icons.Default.Terminal
        else -> Icons.Default.Build
    }

    /** Argumento principal en una línea (ruta, patrón, URL o comando). */
    fun subtitle(name: String, args: JSONObject): String = when (name) {
        "read_file", "write_file", "edit_file" -> args.optString("path")
        "list_dir" -> args.optString("path", ".").ifBlank { "." }
        "glob", "grep" -> args.optString("pattern")
        "http_request" -> "${args.optString("method", "GET").uppercase()} ${args.optString("url")}"
        "web_fetch" -> args.optString("url")
        "run_shell" -> args.optString("command")
        else -> ""
    }
}
