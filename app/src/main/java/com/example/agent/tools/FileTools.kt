package com.example.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

private const val MAX_READ_BYTES = 2L * 1024 * 1024
private const val MAX_WRITE_CHARS = 1_000_000
private const val MAX_LINE_CHARS = 2000

private fun isBinary(file: File): Boolean = file.inputStream().use { input ->
    val buf = ByteArray(8000)
    val n = input.read(buf)
    (0 until maxOf(n, 0)).any { buf[it] == 0.toByte() }
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private val SKIPPED_DIRS = setOf(".git", "node_modules", ".gradle", "build")

class ReadFileTool(private val ws: Workspace) : Tool {
    override val name = "read_file"
    override val description =
        "Lee un archivo de texto del workspace y devuelve sus líneas numeradas. " +
            "Para archivos grandes usa offset y limit."
    override val parameters = schema(
        listOf("path"),
        "path" to prop("string", "Ruta del archivo, relativa al workspace"),
        "offset" to prop("integer", "Primera línea a leer (1 = inicio). Por defecto 1"),
        "limit" to prop("integer", "Máximo de líneas a devolver. Por defecto 2000")
    )
    override val risk = ToolRisk.READ

    override fun summarize(args: JSONObject) = "Leer ${args.optString("path")}"
    override fun validate(args: JSONObject) { ws.resolve(args.requireString("path")) }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.requireString("path")
        val file = ws.resolve(path)
        if (!file.exists()) return@withContext ToolResult.error("No existe: $path")
        if (file.isDirectory) return@withContext ToolResult.error("'$path' es un directorio; usa list_dir")
        if (file.length() > MAX_READ_BYTES) return@withContext ToolResult.error("Archivo demasiado grande (${humanSize(file.length())}); usa grep")
        if (isBinary(file)) return@withContext ToolResult.error("'$path' parece un archivo binario")

        val offset = args.optInt("offset", 1).coerceAtLeast(1)
        val limit = args.optInt("limit", 2000).coerceIn(1, 5000)

        val sb = StringBuilder()
        var total = 0
        var shown = 0
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                ensureActive()
                total++
                if (total >= offset && shown < limit) {
                    val l = if (line.length > MAX_LINE_CHARS) line.take(MAX_LINE_CHARS) + "…" else line
                    sb.append(String.format(Locale.US, "%6d\t%s\n", total, l))
                    shown++
                }
            }
        }
        if (total == 0) return@withContext ToolResult.ok("(archivo vacío)")
        if (shown == 0) return@withContext ToolResult.error("offset $offset está más allá del final ($total líneas)")
        if (offset + shown - 1 < total) {
            sb.append("[Mostrando líneas $offset-${offset + shown - 1} de $total. Usa offset/limit para ver más]\n")
        }
        ToolResult.ok(truncateOutput(sb.toString()))
    }
}

class WriteFileTool(private val ws: Workspace) : Tool {
    override val name = "write_file"
    override val description =
        "Crea un archivo o lo sobrescribe por completo con el contenido dado. " +
            "Crea los directorios intermedios. Para cambios pequeños usa edit_file."
    override val parameters = schema(
        listOf("path", "content"),
        "path" to prop("string", "Ruta del archivo, relativa al workspace"),
        "content" to prop("string", "Contenido completo del archivo")
    )
    override val risk = ToolRisk.WRITE

    override fun summarize(args: JSONObject) = "Escribir ${args.optString("path")}"
    override fun validate(args: JSONObject) {
        ws.resolve(args.requireString("path"))
        val content = args.requireString("content")
        if (content.length > MAX_WRITE_CHARS) throw ToolException("Contenido demasiado grande (máx. $MAX_WRITE_CHARS caracteres)")
    }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.requireString("path")
        val content = args.requireString("content")
        val file = ws.resolve(path)
        if (file.isDirectory) return@withContext ToolResult.error("'$path' es un directorio")
        if (file == ws.root) return@withContext ToolResult.error("Ruta no válida")

        val existed = file.exists()
        file.parentFile?.mkdirs()
        file.writeText(content)
        ToolResult.ok("${if (existed) "Sobrescrito" else "Creado"} $path (${content.toByteArray().size} bytes, ${content.lines().size} líneas)")
    }
}

class EditFileTool(private val ws: Workspace) : Tool {
    override val name = "edit_file"
    override val description =
        "Edita un archivo existente reemplazando un texto EXACTO por otro. old_string debe coincidir " +
            "carácter por carácter (incluida la indentación) y ser único, salvo que uses replace_all. " +
            "Lee el archivo antes de editarlo."
    override val parameters = schema(
        listOf("path", "old_string", "new_string"),
        "path" to prop("string", "Ruta del archivo, relativa al workspace"),
        "old_string" to prop("string", "Texto exacto a reemplazar"),
        "new_string" to prop("string", "Texto nuevo"),
        "replace_all" to prop("boolean", "Reemplazar todas las apariciones. Por defecto false")
    )
    override val risk = ToolRisk.WRITE

    override fun summarize(args: JSONObject) = "Editar ${args.optString("path")}"
    override fun validate(args: JSONObject) {
        ws.resolve(args.requireString("path"))
        if (args.requireString("old_string").isEmpty()) throw ToolException("old_string no puede estar vacío")
        args.requireString("new_string")
    }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.requireString("path")
        val old = args.requireString("old_string")
        val new = args.requireString("new_string")
        val replaceAll = args.optBoolean("replace_all", false)

        val file = ws.resolve(path)
        if (!file.isFile) return@withContext ToolResult.error("No existe el archivo: $path (usa write_file para crearlo)")
        if (file.length() > MAX_READ_BYTES || isBinary(file)) return@withContext ToolResult.error("No se puede editar '$path' (binario o muy grande)")
        if (old == new) return@withContext ToolResult.error("old_string y new_string son idénticos")

        val text = file.readText()
        var count = 0
        var from = 0
        while (true) {
            val i = text.indexOf(old, from)
            if (i < 0) break
            count++
            from = i + old.length
        }
        when {
            count == 0 -> ToolResult.error("No se encontró old_string en $path. Debe coincidir exactamente; vuelve a leer el archivo.")
            count > 1 && !replaceAll -> ToolResult.error("old_string aparece $count veces en $path. Añade más contexto para que sea único o usa replace_all.")
            else -> {
                file.writeText(if (replaceAll) text.replace(old, new) else text.replaceFirst(old, new))
                ToolResult.ok("Edición aplicada en $path ($count reemplazo${if (count == 1) "" else "s"})")
            }
        }
    }
}

class ListDirTool(private val ws: Workspace) : Tool {
    override val name = "list_dir"
    override val description = "Lista el contenido de un directorio del workspace (directorios primero)."
    override val parameters = schema(
        emptyList(),
        "path" to prop("string", "Directorio, relativo al workspace. Por defecto '.' (raíz)")
    )
    override val risk = ToolRisk.READ

    override fun summarize(args: JSONObject) = "Listar ${args.optString("path", ".").ifBlank { "." }}"
    override fun validate(args: JSONObject) { ws.resolve(args.optStringOrNull("path") ?: ".") }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val path = args.optStringOrNull("path") ?: "."
        val dir = ws.resolve(path)
        if (!dir.exists()) return@withContext ToolResult.error("No existe: $path")
        if (!dir.isDirectory) return@withContext ToolResult.error("'$path' no es un directorio")

        val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).orEmpty()
        if (entries.isEmpty()) return@withContext ToolResult.ok("(directorio vacío)")

        val max = 500
        val sb = StringBuilder()
        entries.take(max).forEach { f ->
            if (f.isDirectory) sb.append("d  ${f.name}/\n") else sb.append("f  ${f.name}  (${humanSize(f.length())})\n")
        }
        if (entries.size > max) sb.append("[... ${entries.size - max} entradas más]\n")
        ToolResult.ok(sb.toString())
    }
}

/** Convierte un glob (`**`, `*`, `?`, `{a,b}`) en regex. Sin `/` en el patrón se busca a cualquier profundidad. */
internal fun globToRegex(glob: String): Regex {
    val pattern = if (glob.contains('/')) glob else "**/$glob"
    val sb = StringBuilder("^")
    var i = 0
    var braces = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '*' && pattern.startsWith("**/", i) -> { sb.append("(?:.*/)?"); i += 2 }
            c == '*' && pattern.startsWith("**", i) -> { sb.append(".*"); i += 1 }
            c == '*' -> sb.append("[^/]*")
            c == '?' -> sb.append("[^/]")
            c == '{' -> { sb.append("(?:"); braces++ }
            c == '}' && braces > 0 -> { sb.append(")"); braces-- }
            c == ',' && braces > 0 -> sb.append("|")
            c in ".()+|^$\\[]}" -> sb.append('\\').append(c)
            else -> sb.append(c)
        }
        i++
    }
    return Regex(sb.append("$").toString())
}

private fun File.walkFiles(): Sequence<File> =
    walkTopDown().onEnter { it.name !in SKIPPED_DIRS || it == this }.filter { it.isFile }

class GlobTool(private val ws: Workspace) : Tool {
    override val name = "glob"
    override val description = "Busca archivos por patrón glob (ej. '**/*.kt', 'src/**/*.{js,ts}'). Devuelve rutas relativas al workspace."
    override val parameters = schema(
        listOf("pattern"),
        "pattern" to prop("string", "Patrón glob"),
        "path" to prop("string", "Directorio base. Por defecto la raíz del workspace")
    )
    override val risk = ToolRisk.READ

    override fun summarize(args: JSONObject) = "Buscar archivos ${args.optString("pattern")}"
    override fun validate(args: JSONObject) {
        args.requireString("pattern")
        ws.resolve(args.optStringOrNull("path") ?: ".")
    }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val regex = try {
            globToRegex(args.requireString("pattern"))
        } catch (e: Exception) {
            return@withContext ToolResult.error("Patrón glob inválido")
        }
        val base = ws.resolve(args.optStringOrNull("path") ?: ".")
        if (!base.isDirectory) return@withContext ToolResult.error("El directorio base no existe")

        val matches = mutableListOf<String>()
        for (f in base.walkFiles()) {
            ensureActive()
            val relToBase = f.relativeTo(base).path.replace(File.separatorChar, '/')
            if (regex.matches(relToBase)) matches += ws.relative(f)
            if (matches.size >= 1000) break
        }
        if (matches.isEmpty()) ToolResult.ok("(sin coincidencias)")
        else ToolResult.ok(truncateOutput(matches.sorted().joinToString("\n") + if (matches.size >= 1000) "\n[límite de 1000 resultados]" else ""))
    }
}

class GrepTool(private val ws: Workspace) : Tool {
    override val name = "grep"
    override val description = "Busca una expresión regular en el contenido de los archivos de texto. Devuelve 'ruta:línea: texto'."
    override val parameters = schema(
        listOf("pattern"),
        "pattern" to prop("string", "Expresión regular (sintaxis Java)"),
        "path" to prop("string", "Archivo o directorio donde buscar. Por defecto la raíz"),
        "glob" to prop("string", "Filtra los archivos por glob, ej. '*.kt'"),
        "ignore_case" to prop("boolean", "Ignorar mayúsculas. Por defecto false"),
        "max_results" to prop("integer", "Máximo de coincidencias (por defecto 200, máx. 500)")
    )
    override val risk = ToolRisk.READ

    override fun summarize(args: JSONObject) = "Buscar «${args.optString("pattern")}»"
    override fun validate(args: JSONObject) {
        args.requireString("pattern")
        ws.resolve(args.optStringOrNull("path") ?: ".")
    }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val options = if (args.optBoolean("ignore_case", false)) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val regex = try {
            Regex(args.requireString("pattern"), options)
        } catch (e: Exception) {
            return@withContext ToolResult.error("Expresión regular inválida: ${e.message?.lineSequence()?.first()}")
        }
        val fileFilter = args.optStringOrNull("glob")?.let { globToRegex(it) }
        val target = ws.resolve(args.optStringOrNull("path") ?: ".")
        if (!target.exists()) return@withContext ToolResult.error("No existe la ruta indicada")
        val max = args.optInt("max_results", 200).coerceIn(1, 500)

        val files = if (target.isFile) sequenceOf(target) else target.walkFiles()
        val out = mutableListOf<String>()
        outer@ for (f in files) {
            ensureActive()
            if (f.length() > 1024 * 1024 || isBinary(f)) continue
            if (fileFilter != null && !fileFilter.matches(f.relativeTo(target.takeIf { it.isDirectory } ?: f.parentFile!!).path.replace(File.separatorChar, '/'))) continue
            val rel = ws.relative(f)
            f.bufferedReader().useLines { lines ->
                var n = 0
                for (line in lines) {
                    n++
                    if (line.length <= 5000 && regex.containsMatchIn(line)) {
                        out += "$rel:$n: ${line.trim().take(300)}"
                        if (out.size >= max) return@useLines
                    }
                }
            }
            if (out.size >= max) break@outer
        }
        if (out.isEmpty()) ToolResult.ok("(sin coincidencias)")
        else ToolResult.ok(truncateOutput(out.joinToString("\n") + if (out.size >= max) "\n[límite de $max resultados]" else ""))
    }
}
