package com.example.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class WorkspaceTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var ws: Workspace

    @Before fun setUp() { ws = Workspace(File(tmp.root, "ws")) }

    private fun assertBlocked(path: String) {
        try {
            ws.resolve(path)
            fail("'$path' debía bloquearse")
        } catch (e: PathViolationException) {
            // esperado
        }
    }

    @Test fun relativePaths_resolveInsideTheRoot() {
        assertEquals(File(ws.root, "src/a.kt"), ws.resolve("src/a.kt"))
        assertEquals(ws.root, ws.resolve("."))
        assertEquals(ws.root, ws.resolve(""))
    }

    @Test fun dotDotSegments_areRejected() {
        assertBlocked("..")
        assertBlocked("../secret.txt")
        assertBlocked("a/../../b")
        assertBlocked("a/b/../c")          // incluso si se queda dentro: no se admite '..'
        assertBlocked("..\\windows")
    }

    @Test fun absolutePathsOutside_areRejected() {
        assertBlocked("/etc/passwd")
        assertBlocked(tmp.root.path)       // el padre del workspace
        assertBlocked(File(tmp.root, "ws-hermano/x").path)
    }

    @Test fun absolutePathInsideTheWorkspace_isAllowed() {
        assertEquals(File(ws.root, "x.txt"), ws.resolve(File(ws.root, "x.txt").path))
    }

    @Test fun siblingDirectoryWithSamePrefix_isNotConsideredInside() {
        // /…/ws-evil comparte prefijo de texto con /…/ws
        assertBlocked(File(ws.root.path + "-evil", "x").path)
    }

    @Test fun symlinkPointingOutside_isRejected() {
        val outside = tmp.newFolder("fuera")
        val link = File(ws.root, "enlace")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertBlocked("enlace/secreto.txt")
        assertBlocked("enlace")
    }

    @Test fun nullBytes_areRejected() {
        assertBlocked("a\u0000.txt")
    }

    @Test fun relative_returnsPortablePaths() {
        assertEquals("a/b.txt", ws.relative(File(ws.root, "a/b.txt")))
        assertEquals(".", ws.relative(ws.root))
        assertTrue(ws.root.isDirectory)
    }

    @Test fun missingStoragePermission_blocksResolveWithAClearError() {
        ws.blockedReason = "Falta el permiso de almacenamiento"
        try {
            ws.resolve("a.txt")
            fail("debía bloquearse")
        } catch (e: StoragePermissionException) {
            assertTrue(e.message!!.contains("permiso de almacenamiento"))
        }
        ws.blockedReason = null
        assertEquals(File(ws.root, "a.txt"), ws.resolve("a.txt"))
    }

    @Test fun changeRoot_widensTheAllowedArea_andKeepsBlockingTraversal() {
        val shared = tmp.newFolder("shared")
        File(shared, "Download").mkdirs()
        ws.changeRoot(shared, listOf("/sdcard"))
        assertEquals(listOf("/sdcard"), ws.aliases)
        assertEquals(File(shared.canonicalFile, "Download/x.txt"), ws.resolve("Download/x.txt"))
        assertEquals(File(shared.canonicalFile, "Download/x.txt"), ws.resolve(File(shared, "Download/x.txt").path))
        assertBlocked("../fuera")
        assertBlocked(tmp.newFolder("otro").path)
    }
}
