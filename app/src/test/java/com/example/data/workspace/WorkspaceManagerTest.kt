package com.example.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceManagerTest {
    private val internal = File("/data/user/0/app/files/workspace")
    private val shared = File("/storage/emulated/0")

    private fun decide(supportsDirect: Boolean, granted: Boolean) =
        WorkspaceManager.decide(supportsDirect, granted, internal, shared)

    @Test fun globalPermissionGranted_usesSharedStorage_withSdcardAliases() {
        val info = decide(supportsDirect = true, granted = true)
        assertEquals(AccessStatus.GRANTED, info.status)
        assertEquals(shared, info.root)
        assertTrue("/sdcard" in info.aliases)
        assertFalse("/storage/emulated/0" in info.aliases)
        assertFalse(info.needsAction)
        assertNull(info.message)
    }

    @Test fun globalPermissionMissing_needsActionAndExplainsIt() {
        val info = decide(supportsDirect = true, granted = false)
        assertEquals(AccessStatus.MISSING, info.status)
        assertTrue(info.needsAction)
        assertTrue(info.message!!.contains("Acceso a todos los archivos"))
        assertEquals(internal, info.root)
        assertTrue(info.aliases.isEmpty())
    }

    @Test fun android10_usesInternalFolder_withNotice_evenIfGrantedFlagIsTrue() {
        val info = decide(supportsDirect = false, granted = true)
        assertEquals(AccessStatus.INTERNAL_ONLY, info.status)
        assertEquals(internal, info.root)
        assertFalse(info.needsAction)
        assertTrue(info.message!!.contains("Android 10"))
    }

    @Test fun directAccessSupport_byApiLevel() {
        assertTrue(WorkspaceManager.directAccessSupported(36))
        assertTrue(WorkspaceManager.directAccessSupported(30))
        assertFalse(WorkspaceManager.directAccessSupported(29))
        assertTrue(WorkspaceManager.directAccessSupported(28))
    }
}
