package com.example.agent.permissions

enum class Permission { AUTO, ASK, DENY }

/**
 * Modo global del agente, seleccionable junto al botón de enviar:
 *  - AUTOMATIC: ejecuta acciones y ediciones sin confirmación paso a paso.
 *  - ASK: pide confirmación antes de modificar archivos, usar la red o ejecutar comandos.
 * En ambos modos, una herramienta BLOQUEADA en Ajustes sigue bloqueada y el shell (NEVER_AUTO) sigue pidiendo confirmación.
 */
enum class AgentMode { AUTOMATIC, ASK }

/**
 * Decide si una herramienta se ejecuta sola, requiere aprobación del usuario o está bloqueada.
 *
 *  - Los niveles base se configuran por herramienta ([setLevel]) y se pueden persistir con [onChange].
 *  - "Permitir siempre" de una aprobación concede AUTO solo durante la sesión ([grantForSession]).
 *  - Las herramientas de [NEVER_AUTO] (shell) nunca pasan a AUTO, ni por ajuste ni por concesión de sesión.
 */
class PermissionPolicy(
    initial: Map<String, Permission> = emptyMap(),
    private val onChange: (Map<String, Permission>) -> Unit = {}
) {
    /** Cambia al instante: afecta a la siguiente herramienta que pida el agente, incluso a mitad de una ejecución. */
    @Volatile
    var mode: AgentMode = AgentMode.ASK

    private val levels = HashMap(DEFAULTS).apply { putAll(initial) }
    private val sessionGrants = HashSet<String>()

    @Synchronized
    fun levelOf(tool: String): Permission {
        val level = levels[tool] ?: Permission.ASK
        return if (level == Permission.AUTO && tool in NEVER_AUTO) Permission.ASK else level
    }

    @Synchronized
    fun setLevel(tool: String, level: Permission) {
        levels[tool] = if (level == Permission.AUTO && tool in NEVER_AUTO) Permission.ASK else level
        if (level == Permission.DENY) sessionGrants.remove(tool)
        onChange(HashMap(levels))
    }

    /** Decisión efectiva en este momento. */
    @Synchronized
    fun decide(tool: String): Permission {
        val level = levelOf(tool)
        return when {
            level == Permission.DENY -> Permission.DENY
            tool in NEVER_AUTO -> Permission.ASK
            mode == AgentMode.AUTOMATIC -> Permission.AUTO
            tool in sessionGrants -> Permission.AUTO
            else -> level
        }
    }

    fun canRemember(tool: String): Boolean = tool !in NEVER_AUTO

    @Synchronized
    fun grantForSession(tool: String) {
        if (canRemember(tool) && levelOf(tool) != Permission.DENY) sessionGrants += tool
    }

    @Synchronized
    fun clearSessionGrants() = sessionGrants.clear()

    @Synchronized
    fun snapshot(): Map<String, Permission> = TOOL_NAMES.associateWith { levelOf(it) }

    companion object {
        val NEVER_AUTO = setOf("run_shell")

        val TOOL_NAMES = listOf(
            "read_file", "list_dir", "glob", "grep", "write_file", "edit_file",
            "web_search", "web_fetch", "http_request", "generate_image",
            "list_apps", "device_info", "open_app", "open_url",
            "github_list_repos", "github_tree", "github_read_file", "github_commits", "github_issues", "run_shell"
        )

        val DEFAULTS: Map<String, Permission> = mapOf(
            "read_file" to Permission.AUTO,
            "list_dir" to Permission.AUTO,
            "glob" to Permission.AUTO,
            "grep" to Permission.AUTO,
            "write_file" to Permission.ASK,
            "edit_file" to Permission.ASK,
            "http_request" to Permission.ASK,
            "web_fetch" to Permission.ASK,
            "generate_image" to Permission.AUTO,
            "web_search" to Permission.AUTO,
            "list_apps" to Permission.AUTO,
            // Solo lectura de los repos del propio usuario, que conectó GitHub a propósito
            "github_list_repos" to Permission.AUTO,
            "github_tree" to Permission.AUTO,
            "github_read_file" to Permission.AUTO,
            "github_commits" to Permission.AUTO,
            "github_issues" to Permission.AUTO,
            "device_info" to Permission.AUTO,
            "open_app" to Permission.ASK,
            "open_url" to Permission.ASK,
            "run_shell" to Permission.ASK
        )
    }
}
