package com.example.agent.device

import com.example.agent.tools.ToolException
import com.example.agent.tools.ToolStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeviceToolsTest {
    private class FakeDevice(val apps: List<AppInfo>) : DeviceBridge {
        val launched = mutableListOf<String>()
        val opened = mutableListOf<String>()
        override fun installedApps() = apps
        override fun launchApp(packageName: String) = apps.any { it.packageName == packageName && it.launchable }.also { if (it) launched += packageName }
        override fun openUri(uri: String) = (!uri.startsWith("market:")).also { if (it) opened += uri }
        override fun deviceInfo() = linkedMapOf("Batería" to "80%", "Android" to "16 (API 36)")
    }

    private val apps = listOf(
        AppInfo("WhatsApp", "com.whatsapp", false, "2.24"),
        AppInfo("WhatsApp Business", "com.whatsapp.w4b", false),
        AppInfo("Cámara", "com.android.camera", true),
        AppInfo("YouTube", "com.google.android.youtube", true),
        AppInfo("Servicio oculto", "com.android.hidden", true, launchable = false)
    )
    private val device = FakeDevice(apps)

    @Test fun listApps_countsAndLists() = runBlocking {
        val out = ListAppsTool(device).execute(JSONObject()).output
        assertTrue(out.contains("5 paquetes instalados en total"))
        assertTrue(out.contains("4 apps con icono"))
        assertTrue(out.contains("2 instaladas por el usuario"))
        assertTrue(out.contains("WhatsApp (com.whatsapp)"))
        assertTrue("las apps sin icono no se listan por defecto", !out.contains("com.android.hidden"))
    }

    @Test fun listApps_filtersByQueryIgnoringAccents() = runBlocking {
        val out = ListAppsTool(device).execute(JSONObject().put("query", "camara")).output
        assertTrue(out.contains("Coincidencias con \"camara\": 1"))
        assertTrue(out.contains("Cámara"))
    }

    @Test fun openApp_exactNameWinsOverPrefixMatches() = runBlocking {
        val r = OpenAppTool(device).execute(JSONObject().put("app", "whatsapp"))
        assertEquals(ToolStatus.OK, r.status)
        assertEquals(listOf("com.whatsapp"), device.launched)
    }

    @Test fun openApp_byPackageAndByAccentlessName() = runBlocking {
        OpenAppTool(device).execute(JSONObject().put("app", "com.google.android.youtube"))
        OpenAppTool(device).execute(JSONObject().put("app", "camara"))
        assertEquals(listOf("com.google.android.youtube", "com.android.camera"), device.launched)
    }

    @Test fun openApp_ambiguousOrMissing_explainsWithoutLaunching() = runBlocking {
        val ambiguous = OpenAppTool(device).execute(JSONObject().put("app", "whats"))
        assertEquals(ToolStatus.ERROR, ambiguous.status)
        assertTrue(ambiguous.output.contains("Varias apps"))
        val missing = OpenAppTool(device).execute(JSONObject().put("app", "Telegram"))
        assertTrue(missing.output.contains("list_apps"))
        assertTrue(device.launched.isEmpty())
    }

    @Test fun openUrl_allowsKnownSchemes_only() = runBlocking {
        val tool = OpenUrlTool(device)
        tool.validate(JSONObject().put("url", "https://wa.me/34600000000?text=hola"))
        tool.validate(JSONObject().put("url", "tel:+34600000000"))
        listOf("intent://x#Intent;end", "javascript:alert(1)", "file:///sdcard/a", "content://x").forEach {
            try { tool.validate(JSONObject().put("url", it)); fail("'$it' debía rechazarse") } catch (e: ToolException) { }
        }
        assertEquals(ToolStatus.OK, tool.execute(JSONObject().put("url", "https://example.com")).status)
        assertEquals(ToolStatus.ERROR, tool.execute(JSONObject().put("url", "market://details?id=x")).status)
    }

    @Test fun deviceInfo_formatsAllFields() = runBlocking {
        assertEquals("Batería: 80%\nAndroid: 16 (API 36)", DeviceInfoTool(device).execute(JSONObject()).output)
    }
}
