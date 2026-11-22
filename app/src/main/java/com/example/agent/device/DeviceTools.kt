package com.example.agent.device

import com.example.agent.tools.Tool
import com.example.agent.tools.ToolException
import com.example.agent.tools.ToolResult
import com.example.agent.tools.ToolRisk
import com.example.agent.tools.optStringOrNull
import com.example.agent.tools.prop
import com.example.agent.tools.requireString
import com.example.agent.tools.schema
import org.json.JSONObject
import java.text.Normalizer

data class AppInfo(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val versionName: String? = null,
    /** Tiene icono en el lanzador (se puede abrir). */
    val launchable: Boolean = true
)

/**
 * Puente con las APIs oficiales de Android (PackageManager, Intents, BatteryManager...). Las herramientas
 * solo dependen de esta interfaz, así que se prueban en la JVM con una implementación falsa.
 */
interface DeviceBridge {
    fun installedApps(): List<AppInfo>
    /** Abre la app; false si no tiene actividad de inicio. */
    fun launchApp(packageName: String): Boolean
    /** Abre un enlace con la app que lo gestione (navegador, WhatsApp, mapas, teléfono...). */
    fun openUri(uri: String): Boolean
    fun deviceInfo(): Map<String, String>
}

/** Búsqueda tolerante por nombre visible o paquete: "whatsapp" encuentra "WhatsApp", "camara" encuentra "Cámara". */
object AppMatcher {
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("[^a-z0-9.]+"), " ").trim()

    fun find(apps: List<AppInfo>, query: String): List<AppInfo> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val candidates = apps.filter { it.launchable }
        candidates.filter { it.packageName.equals(query.trim(), ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { return it }
        candidates.filter { normalize(it.label) == q }.takeIf { it.isNotEmpty() }?.let { return it }
        candidates.filter { normalize(it.label).startsWith(q) }.takeIf { it.isNotEmpty() }?.let { return it }
        return candidates.filter { normalize(it.label).contains(q) || it.packageName.lowercase().contains(q.replace(" ", "")) }
    }
}

class ListAppsTool(private val device: DeviceBridge) : Tool {
    override val name = "list_apps"
    override val description =
        "Lista las aplicaciones instaladas en el teléfono (nombre y paquete) usando el PackageManager de Android. " +
            "Sirve para contar apps, comprobar si una está instalada o buscar por nombre. No uses run_shell/pm para esto."
    override val parameters = schema(
        emptyList(),
        "query" to prop("string", "Filtro opcional por nombre o paquete (p. ej. 'whatsapp')"),
        "include_system" to prop("boolean", "Incluir apps del sistema sin icono (por defecto false)"),
        "limit" to prop("integer", "Máximo de apps a listar (por defecto 200)")
    )
    override val risk = ToolRisk.READ

    override fun summarize(args: JSONObject) = args.optStringOrNull("query")?.let { "Buscar app: $it" } ?: "Listar apps instaladas"

    override suspend fun execute(args: JSONObject): ToolResult {
        val all = device.installedApps()
        val includeSystem = args.optBoolean("include_system", false)
        val limit = args.optInt("limit", 200).coerceIn(1, 1000)
        val query = args.optStringOrNull("query")?.takeIf { it.isNotBlank() }

        val launchable = all.filter { it.launchable }
        val userApps = all.count { !it.isSystem }
        val pool = if (includeSystem) all else launchable
        val shown = (if (query != null) pool.filter {
            AppMatcher.normalize(it.label).contains(AppMatcher.normalize(query)) || it.packageName.contains(query.trim(), ignoreCase = true)
        } else pool).sortedBy { it.label.lowercase() }

        return ToolResult.ok(buildString {
            append("Resumen: ${all.size} paquetes instalados en total · ${launchable.size} apps con icono (abribles) · ")
            append("$userApps instaladas por el usuario · ${all.size - userApps} del sistema.\n")
            if (query != null) append("Coincidencias con \"$query\": ${shown.size}\n")
            append('\n')
            shown.take(limit).forEach { a ->
                append("- ${a.label} (${a.packageName})")
                if (a.isSystem) append(" [sistema]")
                a.versionName?.let { append(" v$it") }
                append('\n')
            }
            if (shown.size > limit) append("… y ${shown.size - limit} más (usa query para filtrar)\n")
        }.trimEnd())
    }
}

class OpenAppTool(private val device: DeviceBridge) : Tool {
    override val name = "open_app"
    override val description =
        "Abre una aplicación instalada (WhatsApp, Cámara, YouTube, Ajustes...) por su nombre visible o su paquete, " +
            "con un Intent oficial de Android."
    override val parameters = schema(
        listOf("app"),
        "app" to prop("string", "Nombre de la app (p. ej. 'WhatsApp') o paquete (com.whatsapp)")
    )
    override val risk = ToolRisk.EXEC

    override fun summarize(args: JSONObject) = "Abrir ${args.optString("app")}"

    override fun validate(args: JSONObject) {
        if (args.requireString("app").isBlank()) throw ToolException("Indica qué app abrir")
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val query = args.requireString("app")
        val matches = AppMatcher.find(device.installedApps(), query)
        return when {
            matches.isEmpty() -> ToolResult.error("No hay ninguna app instalada que coincida con \"$query\". Usa list_apps con query para buscarla.")
            matches.size > 1 && matches.map { it.label.lowercase() }.distinct().size > 1 && AppMatcher.normalize(matches[0].label) != AppMatcher.normalize(query) ->
                ToolResult.error(
                    "Varias apps coinciden con \"$query\": " + matches.take(8).joinToString { "${it.label} (${it.packageName})" } +
                        ". Vuelve a llamar con el nombre exacto o el paquete."
                )
            else -> {
                val app = matches.first()
                if (device.launchApp(app.packageName)) ToolResult.ok("Abierta: ${app.label} (${app.packageName}). El usuario la está viendo ahora.")
                else ToolResult.error("${app.label} no se puede abrir directamente (no tiene pantalla de inicio).")
            }
        }
    }
}

class OpenUrlTool(private val device: DeviceBridge) : Tool {
    override val name = "open_url"
    override val description =
        "Abre un enlace en la app adecuada del teléfono: páginas web (https://), WhatsApp a un número (https://wa.me/<número>?text=...), " +
            "llamadas (tel:), correo (mailto:), mapas (geo:) o la tienda (market://details?id=...)."
    override val parameters = schema(listOf("url"), "url" to prop("string", "Enlace a abrir"))
    override val risk = ToolRisk.EXEC

    override fun summarize(args: JSONObject) = "Abrir ${args.optString("url")}"

    override fun validate(args: JSONObject) {
        val url = args.requireString("url").trim()
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme !in ALLOWED_SCHEMES) {
            throw ToolException("Esquema no permitido: '$scheme'. Permitidos: ${ALLOWED_SCHEMES.joinToString()}")
        }
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val url = args.requireString("url").trim()
        return if (device.openUri(url)) ToolResult.ok("Abierto: $url")
        else ToolResult.error("Ninguna app del teléfono puede abrir: $url")
    }

    companion object {
        val ALLOWED_SCHEMES = setOf("http", "https", "tel", "mailto", "geo", "market", "sms", "smsto", "whatsapp")
    }
}

class DeviceInfoTool(private val device: DeviceBridge) : Tool {
    override val name = "device_info"
    override val description =
        "Información del teléfono: modelo, versión de Android, batería, almacenamiento libre/total, memoria RAM, idioma, " +
            "zona horaria y fecha/hora actual."
    override val parameters = schema(emptyList())
    override val risk = ToolRisk.READ

    override fun summarize(args: JSONObject) = "Información del dispositivo"

    override suspend fun execute(args: JSONObject): ToolResult =
        ToolResult.ok(device.deviceInfo().entries.joinToString("\n") { (k, v) -> "$k: $v" })
}
