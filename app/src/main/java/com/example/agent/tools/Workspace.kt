package com.example.agent.tools

import java.io.File

class PathViolationException(message: String) : ToolException(message)

/** Falta el permiso de almacenamiento de Android (Acceso a todos los archivos). */
class StoragePermissionException(message: String) : ToolException(message)

/**
 * Directorio de trabajo del agente. TODAS las herramientas de archivos resuelven rutas con
 * [resolve], que impide salir del workspace:
 *  - rechaza cualquier segmento `..` (path traversal),
 *  - rechaza rutas con bytes nulos,
 *  - resuelve enlaces simbólicos (canonical) y comprueba que el destino siga dentro del root.
 */
class Workspace(rootDir: File) {
    /** Raíz activa. Puede cambiar en caliente (carpeta interna ↔ almacenamiento compartido completo). */
    @Volatile
    var root: File = rootDir.also { it.mkdirs() }.canonicalFile
        private set

    /** Rutas equivalentes a [root] (p. ej. `/sdcard` ↔ `/storage/emulated/0`); el shell las acepta tal cual. */
    @Volatile
    var aliases: List<String> = emptyList()
        private set

    /**
     * Si no es null, falta el permiso de almacenamiento: ninguna herramienta de archivos ni el shell
     * pueden operar, y el fallo avisa para que la app solicite el permiso (no falla en silencio).
     */
    @Volatile
    var blockedReason: String? = null

    fun changeRoot(dir: File, aliases: List<String> = emptyList()) {
        dir.mkdirs()
        root = dir.canonicalFile
        this.aliases = aliases
    }

    fun ensureAccessible() {
        blockedReason?.let { throw StoragePermissionException(it) }
    }

    fun resolve(path: String): File {
        ensureAccessible()
        val p = path.trim().ifEmpty { "." }
        if (p.contains('\u0000')) throw PathViolationException("Ruta no válida")
        if (p.split('/', '\\').any { it == ".." }) {
            throw PathViolationException("Ruta no permitida: no se admite '..' (path traversal)")
        }

        val candidate = if (p.startsWith("/")) File(p) else File(root, p)
        val canonical = try {
            candidate.canonicalFile
        } catch (e: java.io.IOException) {
            throw PathViolationException("No se pudo resolver la ruta: $p")
        }

        val inside = canonical == root || canonical.path.startsWith(root.path + File.separator)
        if (!inside) {
            throw PathViolationException(
                "Ruta fuera del área accesible: $p. Solo puedes trabajar dentro de ${root.path} " +
                    "(rutas como /sdcard/Download/... o relativas a esa carpeta)."
            )
        }
        return canonical
    }

    /** Ruta relativa al root, con `/` como separador; "." para el propio root. */
    fun relative(file: File): String {
        val c = file.canonicalFile
        return if (c == root) "." else c.path.removePrefix(root.path + File.separator).replace(File.separatorChar, '/')
    }
}
