package com.example.agent.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {

    @Test
    fun defaults_readToolsAreAuto_writeAndNetworkAndShellAsk() {
        val p = PermissionPolicy()
        listOf("read_file", "list_dir", "glob", "grep").forEach { assertEquals(it, Permission.AUTO, p.decide(it)) }
        listOf("write_file", "edit_file", "http_request", "web_fetch", "run_shell").forEach { assertEquals(it, Permission.ASK, p.decide(it)) }
    }

    @Test
    fun unknownTool_defaultsToAsk() {
        assertEquals(Permission.ASK, PermissionPolicy().decide("herramienta_nueva"))
    }

    @Test
    fun deny_blocksTheTool() {
        val p = PermissionPolicy()
        p.setLevel("read_file", Permission.DENY)
        assertEquals(Permission.DENY, p.decide("read_file"))
    }

    @Test
    fun sessionGrant_turnsAskIntoAuto_untilCleared() {
        val p = PermissionPolicy()
        p.grantForSession("write_file")
        assertEquals(Permission.AUTO, p.decide("write_file"))
        p.clearSessionGrants()
        assertEquals(Permission.ASK, p.decide("write_file"))
    }

    @Test
    fun shell_neverBecomesAuto_neitherBySettingNorBySessionGrant() {
        val p = PermissionPolicy(initial = mapOf("run_shell" to Permission.AUTO))
        assertEquals(Permission.ASK, p.decide("run_shell"))
        p.setLevel("run_shell", Permission.AUTO)
        assertEquals(Permission.ASK, p.levelOf("run_shell"))
        p.grantForSession("run_shell")
        assertEquals(Permission.ASK, p.decide("run_shell"))
        assertFalse(p.canRemember("run_shell"))
        assertTrue(p.canRemember("write_file"))
    }

    @Test
    fun shell_canStillBeDenied() {
        val p = PermissionPolicy()
        p.setLevel("run_shell", Permission.DENY)
        assertEquals(Permission.DENY, p.decide("run_shell"))
    }

    @Test
    fun denyRevokesAnExistingSessionGrant() {
        val p = PermissionPolicy()
        p.grantForSession("write_file")
        p.setLevel("write_file", Permission.DENY)
        assertEquals(Permission.DENY, p.decide("write_file"))
        p.setLevel("write_file", Permission.ASK)
        assertEquals(Permission.ASK, p.decide("write_file")) // la concesión se perdió
    }

    @Test
    fun sessionGrantIsIgnoredForDeniedTool() {
        val p = PermissionPolicy(initial = mapOf("edit_file" to Permission.DENY))
        p.grantForSession("edit_file")
        assertEquals(Permission.DENY, p.decide("edit_file"))
    }

    @Test
    fun changes_areReportedForPersistence() {
        var saved: Map<String, Permission>? = null
        val p = PermissionPolicy(onChange = { saved = it })
        p.setLevel("grep", Permission.ASK)
        assertEquals(Permission.ASK, saved!!["grep"])
    }

    @Test
    fun initialLevels_overrideDefaults() {
        val p = PermissionPolicy(initial = mapOf("write_file" to Permission.AUTO, "read_file" to Permission.DENY))
        assertEquals(Permission.AUTO, p.decide("write_file"))
        assertEquals(Permission.DENY, p.decide("read_file"))
    }
}
