package com.example.ui.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PhoneAndroid
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
        "generate_image" -> "Generar imagen"
        "web_search" -> "Buscar en la web"
        "github_list_repos" -> "Repos de GitHub"
        "github_tree" -> "Árbol del repo"
        "github_read_file" -> "Leer archivo del repo"
        "github_commits" -> "Commits del repo"
        "github_issues" -> "Issues del repo"
        "list_apps" -> "Apps instaladas"
        "open_app" -> "Abrir app"
        "open_url" -> "Abrir enlace"
        "device_info" -> "Info del dispositivo"
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
        "generate_image" -> Icons.Default.Image
        "web_search" -> Icons.Default.TravelExplore
        "github_list_repos", "github_tree", "github_commits", "github_issues" -> Icons.Default.Source
        "github_read_file" -> Icons.Default.Code
        "list_apps" -> Icons.Default.Apps
        "open_app" -> Icons.AutoMirrored.Filled.Launch
        "open_url" -> Icons.Default.Link
        "device_info" -> Icons.Default.PhoneAndroid
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
        "generate_image" -> args.optString("prompt")
        "web_search" -> args.optString("query")
        "github_list_repos" -> args.optString("query").ifBlank { "todos" }
        "github_tree", "github_commits", "github_issues" -> args.optString("repo")
        "github_read_file" -> args.optString("repo") + "/" + args.optString("path")
        "list_apps" -> args.optString("query").ifBlank { "todas" }
        "open_app" -> args.optString("app")
        "open_url" -> args.optString("url")
        else -> ""
    }
}
