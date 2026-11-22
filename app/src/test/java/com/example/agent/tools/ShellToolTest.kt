package com.example.agent.tools

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShellToolTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var ws: Workspace
    private lateinit var shell: ShellTool

    @Before fun setUp() {
        assumeTrue("necesita /bin/sh", File("/bin/sh").exists())
        ws = Workspace(File(tmp.root, "ws"))
        shell = ShellTool(ws, shellPath = "/bin/sh", pathEnv = "/usr/bin:/bin")
    }

    private fun exec(cmd: String, timeout: Int? = null): ToolResult = runBlocking {
        shell.execute(JSONObject().put("command", cmd).also { o -> timeout?.let { o.put("timeout_seconds", it) } })
    }

    // --- ejecución
    @Test fun runsInTheWorkspaceDirectory_andCapturesOutputAndExitCode() {
        val r = exec("pwd; echo hola")
        assertEquals(ToolStatus.OK, r.status)
        assertTrue(r.output.contains(ws.root.path))
        assertTrue(r.output.contains("hola"))
        assertTrue(r.output.endsWith("[código de salida 0]"))
    }

    @Test fun nonZeroExit_isAnErrorResultWithTheCode() {
        val r = exec("echo fallo >&2; exit 3")
        assertEquals(ToolStatus.ERROR, r.status)
        assertTrue(r.output.contains("fallo"))
        assertTrue(r.output.contains("[código de salida 3]"))
    }

    @Test fun filesCreatedByTheShell_liveInTheWorkspace() {
        exec("echo contenido > salida.txt")
        assertEquals("contenido", File(ws.root, "salida.txt").readText().trim())
    }

    @Test fun environmentIsScrubbed() {
        val r = exec("echo \"HOME=\$HOME\"; echo \"USER=\${USER:-vacio}\"")
        assertTrue(r.output.contains("HOME=${ws.root.path}"))
        assertTrue(r.output.contains("USER=vacio"))
    }

    @Test fun timeout_killsTheProcess() {
        val start = System.nanoTime()
        val r = exec("sleep 30", timeout = 1)
        assertEquals(ToolStatus.ERROR, r.status)
        assertTrue(r.output.contains("tiempo agotado"))
        assertTrue("tardó demasiado: ${(System.nanoTime() - start) / 1_000_000} ms", (System.nanoTime() - start) / 1_000_000 < 8_000)
    }

    @Test fun cancellation_killsTheProcessImmediately() {
        val marker = File(ws.root, "no-debe-existir.txt")
        runBlocking {
            val job = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                shell.execute(JSONObject().put("command", "sleep 5; touch no-debe-existir.txt").put("timeout_seconds", 60))
            }
            delay(300)
            val start = System.nanoTime()
            job.cancel()
            runCatching { job.await() }
            assertTrue("la cancelación tardó ${(System.nanoTime() - start) / 1_000_000} ms", (System.nanoTime() - start) / 1_000_000 < 3_000)
        }
        Thread.sleep(300)
        assertFalse("el proceso debía morir antes de crear el archivo", marker.exists())
    }

    @Test fun largeOutput_isTruncated() {
        val r = exec("i=0; while [ \$i -lt 3000 ]; do echo linea-numero-\$i-con-bastante-texto-de-relleno; i=\$((i+1)); done")
        assertTrue(r.output.contains("salida truncada"))
        assertTrue(r.output.length < MAX_OUTPUT_CHARS + 500)
    }

    // --- ShellGuard
    private fun violation(cmd: String) = ShellGuard.violation(cmd, "/data/user/0/app/files/workspace")

    @Test fun guard_allowsNormalWorkspaceCommands() {
        listOf(
            "ls -la", "cat notas.txt | grep hola | wc -l", "mkdir -p src/app && echo hi > src/app/a.txt",
            "echo \"I am here, pm and dd are words\"", "grep -rn TODO .", "cat /system/bin/sh | head -c 10",
            "ls /data/user/0/app/files/workspace/src", "echo x > /dev/null"
        ).forEach { assertNull("'$it' debía permitirse: ${violation(it)}", violation(it)) }
    }

    @Test fun guard_blocksPathTraversal() {
        listOf("cat ../secreto", "ls ..", "cd .. && ls", "cat a/../../b", "ls;..").forEach {
            assertNotNull("'$it' debía bloquearse", violation(it))
        }
    }

    @Test fun guard_blocksAbsolutePathsOutsideTheWorkspace() {
        listOf(
            "cat /etc/hosts", "ls /", "rm -rf /", "cat /data/data/com.aistudio.devmatrix.hcwypt/shared_prefs/x.xml",
            "ls /sdcard", "echo hi > /data/user/0/otra/app", "cat /data/user/0/app/files/workspace-otro/x"
        ).forEach { assertNotNull("'$it' debía bloquearse", violation(it)) }
    }

    @Test fun guard_blocksDangerousAndNetworkCommands_inCommandPosition() {
        listOf(
            "su", "sudo ls", "reboot", "pm uninstall x", "am start x", "curl http://x", "wget x", "nc -l 1", "dd if=a of=b",
            "ls; reboot", "ls && curl x", "echo a | nc host 1", "FOO=1 su", "env su", "xargs sudo ls", "/system/bin/su", "(reboot)", "ls `su`"
        ).forEach { assertNotNull("'$it' debía bloquearse", violation(it)) }
    }

    @Test fun guard_rejectsEmptyCommand() {
        assertNotNull(violation("   "))
    }

    @Test fun validate_throwsPathViolationForBlockedCommands() {
        try {
            shell.validate(JSONObject().put("command", "cat /etc/passwd"))
            throw AssertionError("debía lanzar")
        } catch (e: PathViolationException) {
            assertTrue(e.message!!.contains("/etc/passwd"))
        }
    }
}
