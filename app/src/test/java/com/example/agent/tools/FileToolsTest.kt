package com.example.agent.tools

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileToolsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var ws: Workspace

    @Before fun setUp() { ws = Workspace(File(tmp.root, "ws")) }

    private fun run(tool: Tool, json: String) = runBlocking { tool.execute(JSONObject(json)) }

    // --- write_file
    @Test fun write_createsFileAndParents_thenOverwrites() {
        val w = WriteFileTool(ws)
        val r1 = run(w, """{"path":"src/app/Main.kt","content":"fun main() {}"}""")
        assertEquals(ToolStatus.OK, r1.status)
        assertTrue(r1.output.startsWith("Creado"))
        assertEquals("fun main() {}", File(ws.root, "src/app/Main.kt").readText())
        assertTrue(run(w, """{"path":"src/app/Main.kt","content":"x"}""").output.startsWith("Sobrescrito"))
    }

    @Test fun write_rejectsTraversalAtValidation() {
        try {
            WriteFileTool(ws).validate(JSONObject("""{"path":"../fuera.txt","content":"x"}"""))
            throw AssertionError("debía lanzar")
        } catch (e: PathViolationException) {
            assertTrue(e.message!!.contains(".."))
        }
        assertFalse(File(tmp.root, "fuera.txt").exists())
    }

    @Test fun write_missingContentIsAToolException() {
        try {
            WriteFileTool(ws).validate(JSONObject("""{"path":"a.txt"}"""))
            throw AssertionError("debía lanzar")
        } catch (e: ToolException) {
            assertTrue(e.message!!.contains("content"))
        }
    }

    // --- read_file
    @Test fun read_returnsNumberedLines_andSupportsOffsetLimit() {
        File(ws.root, "a.txt").writeText((1..10).joinToString("\n") { "línea $it" })
        val all = run(ReadFileTool(ws), """{"path":"a.txt"}""")
        assertTrue(all.output.contains("     1\tlínea 1"))
        assertTrue(all.output.contains("    10\tlínea 10"))

        val part = run(ReadFileTool(ws), """{"path":"a.txt","offset":4,"limit":2}""")
        assertTrue(part.output.contains("     4\tlínea 4"))
        assertTrue(part.output.contains("     5\tlínea 5"))
        assertFalse(part.output.contains("línea 6"))
        assertTrue(part.output.contains("Mostrando líneas 4-5 de 10"))
    }

    @Test fun read_errorsAreReturnedNotThrown() {
        assertEquals(ToolStatus.ERROR, run(ReadFileTool(ws), """{"path":"no-existe.txt"}""").status)
        File(ws.root, "d").mkdir()
        assertTrue(run(ReadFileTool(ws), """{"path":"d"}""").output.contains("list_dir"))
        File(ws.root, "bin.dat").writeBytes(byteArrayOf(1, 2, 0, 3))
        assertTrue(run(ReadFileTool(ws), """{"path":"bin.dat"}""").output.contains("binario"))
    }

    @Test fun read_emptyFile() {
        File(ws.root, "vacio.txt").writeText("")
        assertEquals("(archivo vacío)", run(ReadFileTool(ws), """{"path":"vacio.txt"}""").output)
    }

    // --- edit_file
    @Test fun edit_replacesExactUniqueText() {
        File(ws.root, "a.txt").writeText("uno\ndos\ntres\n")
        val r = run(EditFileTool(ws), """{"path":"a.txt","old_string":"dos","new_string":"DOS"}""")
        assertEquals(ToolStatus.OK, r.status)
        assertEquals("uno\nDOS\ntres\n", File(ws.root, "a.txt").readText())
    }

    @Test fun edit_failsWhenNotFound_orAmbiguous_withoutTouchingTheFile() {
        File(ws.root, "a.txt").writeText("x\nx\n")
        val missing = run(EditFileTool(ws), """{"path":"a.txt","old_string":"zzz","new_string":"y"}""")
        assertEquals(ToolStatus.ERROR, missing.status)
        val ambiguous = run(EditFileTool(ws), """{"path":"a.txt","old_string":"x","new_string":"y"}""")
        assertEquals(ToolStatus.ERROR, ambiguous.status)
        assertTrue(ambiguous.output.contains("2 veces"))
        assertEquals("x\nx\n", File(ws.root, "a.txt").readText())
    }

    @Test fun edit_replaceAll() {
        File(ws.root, "a.txt").writeText("x\nx\n")
        val r = run(EditFileTool(ws), """{"path":"a.txt","old_string":"x","new_string":"y","replace_all":true}""")
        assertTrue(r.output.contains("2 reemplazos"))
        assertEquals("y\ny\n", File(ws.root, "a.txt").readText())
    }

    @Test fun edit_onMissingFile_suggestsWriteFile() {
        assertTrue(run(EditFileTool(ws), """{"path":"nuevo.txt","old_string":"a","new_string":"b"}""").output.contains("write_file"))
    }

    // --- list_dir
    @Test fun listDir_showsDirectoriesFirst() {
        File(ws.root, "b.txt").writeText("hi")
        File(ws.root, "carpeta").mkdir()
        File(ws.root, "a.txt").writeText("hello")
        val lines = run(ListDirTool(ws), "{}").output.lines().filter { it.isNotBlank() }
        assertEquals("d  carpeta/", lines[0])
        assertTrue(lines[1].startsWith("f  a.txt"))
        assertTrue(lines[2].startsWith("f  b.txt"))
    }

    @Test fun listDir_emptyAndMissing() {
        assertEquals("(directorio vacío)", run(ListDirTool(ws), "{}").output)
        assertEquals(ToolStatus.ERROR, run(ListDirTool(ws), """{"path":"nope"}""").status)
    }

    // --- glob
    @Test fun glob_matchesPatternsAtAnyDepth() {
        File(ws.root, "src/a").mkdirs()
        File(ws.root, "src/Main.kt").writeText("")
        File(ws.root, "src/a/Util.kt").writeText("")
        File(ws.root, "src/a/notas.md").writeText("")
        File(ws.root, "build").mkdir()
        File(ws.root, "build/Gen.kt").writeText("")

        val kt = run(GlobTool(ws), """{"pattern":"**/*.kt"}""").output.lines()
        assertEquals(listOf("src/Main.kt", "src/a/Util.kt"), kt) // `build/` se omite
        assertEquals(listOf("src/a/notas.md"), run(GlobTool(ws), """{"pattern":"*.md"}""").output.lines())
        assertEquals(listOf("src/Main.kt"), run(GlobTool(ws), """{"pattern":"src/*.kt"}""").output.lines())
        assertEquals(
            listOf("src/Main.kt", "src/a/Util.kt", "src/a/notas.md"),
            run(GlobTool(ws), """{"pattern":"src/**/*.{kt,md}"}""").output.lines()
        )
    }

    @Test fun glob_noMatches() {
        assertEquals("(sin coincidencias)", run(GlobTool(ws), """{"pattern":"*.zzz"}""").output)
    }

    // --- grep
    @Test fun grep_findsMatchesWithFileAndLine() {
        File(ws.root, "a.txt").writeText("hola\nTODO: arreglar\nfin")
        File(ws.root, "sub").mkdir()
        File(ws.root, "sub/b.txt").writeText("nada\nTODO: otra")
        val out = run(GrepTool(ws), """{"pattern":"TODO"}""").output.lines()
        assertEquals(listOf("a.txt:2: TODO: arreglar", "sub/b.txt:2: TODO: otra"), out.sorted())
    }

    @Test fun grep_options_ignoreCase_glob_andInvalidRegex() {
        File(ws.root, "a.txt").writeText("Hello")
        File(ws.root, "b.md").writeText("Hello")
        assertEquals("(sin coincidencias)", run(GrepTool(ws), """{"pattern":"hello"}""").output)
        assertEquals(2, run(GrepTool(ws), """{"pattern":"hello","ignore_case":true}""").output.lines().size)
        assertEquals(listOf("b.md:1: Hello"), run(GrepTool(ws), """{"pattern":"hello","ignore_case":true,"glob":"*.md"}""").output.lines())
        assertEquals(ToolStatus.ERROR, run(GrepTool(ws), """{"pattern":"("}""").status)
    }

    @Test fun grep_skipsBinaryFiles_andHonoursMaxResults() {
        File(ws.root, "bin").writeBytes(byteArrayOf(0, 1, 2, 'a'.code.toByte()))
        File(ws.root, "t.txt").writeText((1..50).joinToString("\n") { "match $it" })
        val out = run(GrepTool(ws), """{"pattern":"match","max_results":3}""").output
        assertEquals(3, out.lines().count { it.startsWith("t.txt:") })
        assertFalse(out.contains("bin"))
    }

    @Test fun readTools_cannotEscapeViaAbsolutePath() {
        File(tmp.root, "secreto.txt").writeText("no")
        try {
            ReadFileTool(ws).validate(JSONObject("""{"path":"${File(tmp.root, "secreto.txt").path}"}"""))
            throw AssertionError("debía lanzar")
        } catch (e: PathViolationException) { /* esperado */ }
    }
}
