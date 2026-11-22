package com.example.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Comprobaciones previas de un comando de shell. NO es un sandbox del sistema operativo: un shell
 * con el UID de la app puede hacer más de lo que esta lista detecta. Es una defensa en profundidad
 * sobre la aprobación obligatoria del usuario (run_shell nunca se ejecuta en modo automático).
 */
object ShellGuard {
    private val BLOCKED_COMMANDS = setOf(
        "su", "sudo", "reboot", "shutdown", "pm", "am", "svc", "settings", "setprop", "mkfs", "dd",
        "mount", "umount", "chown", "nc", "ncat", "netcat", "telnet", "curl", "wget", "ssh", "scp"
    )
    private val DOT_DOT = Regex("""(^|[\s/"'=:;|&(])\.\.($|[\s/"';|&)])""")
    private val ABSOLUTE = Regex("""(?<![\w.\-])/[^\s"'`;|&<>()]*""")
    private val SAFE_PREFIXES = listOf("/system/bin", "/system/xbin", "/dev/null", "/dev/zero", "/dev/urandom", "/dev/stdout", "/dev/stderr")

    private val WRAPPERS = setOf("env", "exec", "nohup", "time", "xargs", "command", "busybox", "toybox", "sh", "bash", "timeout")
    private val SEGMENT_SPLIT = Regex("""[;&|\n(`]+|\$\(""")

    /** Busca comandos bloqueados SOLO en posición de comando (no en argumentos ni texto entre comillas). */
    private fun blockedCommandIn(command: String): String? {
        for (segment in command.split(SEGMENT_SPLIT)) {
            val tokens = segment.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() && !it.contains('=') }
            for (token in tokens) {
                val bin = token.trim('"', '\'', '{', '}', '(', ')').substringAfterLast('/')
                if (bin in BLOCKED_COMMANDS) return bin
                if (bin !in WRAPPERS && !bin.startsWith("-")) break
            }
        }
        return null
    }

    /** Devuelve el motivo del bloqueo, o null si el comando pasa las comprobaciones. */
    fun violation(command: String, workspaceRoot: String, aliases: List<String> = emptyList()): String? {
        if (command.isBlank()) return "El comando está vacío"
        if (command.contains('\u0000')) return "Comando no válido"
        if (DOT_DOT.containsMatchIn(command)) return "No se admiten rutas con '..'"

        ABSOLUTE.findAll(command).forEach { m ->
            val p = m.value
            val inWorkspace = (listOf(workspaceRoot) + aliases).any { p == it || p.startsWith("$it/") }
            val safe = SAFE_PREFIXES.any { p == it || p.startsWith("$it/") }
            if (!inWorkspace && !safe) return "Ruta fuera del workspace: $p"
        }

        blockedCommandIn(command)?.let { return "Comando bloqueado: $it" }
        return null
    }
}

/**
 * Mata un proceso Y todos sus descendientes. `Process.destroyForcibly()` solo mata el `sh`; sus hijos
 * (p. ej. `sleep 30` dentro de `sh -c`) sobreviven y mantienen abierto el pipe de salida, así que ni el
 * tiempo límite ni el botón de detener funcionarían. Los descendientes se localizan en /proc.
 */
internal object ProcessTree {
    private val PID = Regex("""pid=(\d+)""")
    private val KILL_BINARIES = listOf("/system/bin/kill", "/bin/kill", "/usr/bin/kill")

    fun kill(process: Process) {
        val pid = PID.find(process.toString())?.groupValues?.get(1)?.toIntOrNull()
        // Se localizan los descendientes ANTES de matar nada, y se mata primero al shell padre: si cayeran
        // antes los hijos, el shell seguiría con el siguiente comando de la línea (`sleep 5; touch x`).
        val victims = if (pid != null) descendantsOf(pid) else emptyList()
        process.destroyForcibly()
        val killBin = KILL_BINARIES.firstOrNull { File(it).exists() }
        if (victims.isNotEmpty() && killBin != null) {
            runCatching {
                ProcessBuilder(listOf(killBin, "-9") + victims.map { it.toString() })
                    .redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor()
            }
        }
    }

    /** Descendientes de [root], los más profundos primero. */
    fun descendantsOf(root: Int): List<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            val ppid = runCatching {
                File(dir, "stat").readText().substringAfterLast(')').trim().split(' ')[1].toInt()
            }.getOrNull() ?: return@forEach
            children.getOrPut(ppid) { mutableListOf() }.add(pid)
        }
        val ordered = ArrayList<Int>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            children[current]?.forEach { ordered += it; queue.addLast(it) }
        }
        return ordered.reversed()
    }
}

class ShellTool(
    private val ws: Workspace,
    private val shellPath: String = "/system/bin/sh",
    private val pathEnv: String = "/system/bin:/system/xbin"
) : Tool {
    override val name = "run_shell"
    override val description =
        "Ejecuta un comando de shell de Android (toybox: ls, cat, grep, find, sed, wc, head, tail, mkdir, cp, mv, rm, echo...) " +
            "con el workspace como directorio actual. No hay bash, git, python ni acceso a red. " +
            "Las rutas deben quedar dentro del almacenamiento accesible (p. ej. /sdcard/Download). Salida combinada stdout+stderr con el código de salida."
    override val parameters = schema(
        listOf("command"),
        "command" to prop("string", "Comando a ejecutar con sh -c"),
        "timeout_seconds" to prop("integer", "Tiempo máximo en segundos (por defecto 30, máx. 120)")
    )
    override val risk = ToolRisk.EXEC

    override fun summarize(args: JSONObject) = "$ ${args.optString("command")}"
    override fun validate(args: JSONObject) {
        val cmd = args.requireString("command")
        ws.ensureAccessible()
        ShellGuard.violation(cmd, ws.root.path, ws.aliases)?.let { throw PathViolationException(it) }
    }

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val command = args.requireString("command")
        val timeoutMs = args.optInt("timeout_seconds", 30).coerceIn(1, 120) * 1000L

        val pb = ProcessBuilder(shellPath, "-c", command)
            .directory(ws.root)
            .redirectErrorStream(true)
        pb.environment().apply {
            clear()
            put("HOME", ws.root.path)
            put("TMPDIR", ws.root.path)
            put("PWD", ws.root.path)
            put("PATH", pathEnv)
            put("TERM", "dumb")
        }

        val process = try {
            pb.start()
        } catch (e: java.io.IOException) {
            return@withContext ToolResult.error("No se pudo iniciar el shell: ${e.message}")
        }
        process.outputStream.close()

        var timedOut = false
        val output = ByteArrayOutputStream()
        coroutineScope {
            // Si la corrutina se cancela (botón detener), el proceso se mata al instante
            val killer = launch { try { awaitCancellation() } finally { ProcessTree.kill(process) } }
            val reader = async(Dispatchers.IO) {
                val buf = ByteArray(4096)
                process.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        // Se sigue leyendo (para no bloquear al proceso) pero solo se guarda hasta el límite
                        if (output.size() < 200_000) output.write(buf, 0, n)
                    }
                }
            }
            if (withTimeoutOrNull(timeoutMs) { reader.await() } == null) {
                timedOut = true
                ProcessTree.kill(process)
                reader.await()
            }
            process.waitFor()
            killer.cancel()
        }

        val text = truncateOutput(output.toString(Charsets.UTF_8.name()).trimEnd())
        if (timedOut) return@withContext ToolResult.error("$text\n[tiempo agotado tras ${timeoutMs / 1000}s; proceso terminado]".trimStart())
        val code = process.exitValue()
        val body = if (text.isBlank()) "(sin salida)" else text
        if (code == 0) ToolResult.ok("$body\n[código de salida 0]") else ToolResult.error("$body\n[código de salida $code]")
    }
}
