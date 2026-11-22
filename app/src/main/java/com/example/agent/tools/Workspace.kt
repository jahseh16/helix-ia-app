package com.example.agent.tools

import java.io.File

class PathViolationException(message: String) : ToolException(message)

/**
 * Directorio de trabajo del agente. TODAS las herramientas de archivos resuelven rutas con
 * [resolve], que impide salir del workspace:
 *  - rechaza cualquier segmento `..` (path traversal),
 *  - rechaza rutas con bytes nulos,
 *  - resuelve enlaces simbólicos (canonical) y comprueba que el destino siga dentro del root.
 */
class Workspace(rootDir: File) {
    val root: File

    init {
        rootDir.mkdirs()
        root = rootDir.canonicalFile
    }

    fun resolve(path: String): File {
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
        if (!inside) throw PathViolationException("Ruta fuera del workspace: $p")
        return canonical
    }

    /** Ruta relativa al root, con `/` como separador; "." para el propio root. */
    fun relative(file: File): String {
        val c = file.canonicalFile
        return if (c == root) "." else c.path.removePrefix(root.path + File.separator).replace(File.separatorChar, '/')
    }
}
