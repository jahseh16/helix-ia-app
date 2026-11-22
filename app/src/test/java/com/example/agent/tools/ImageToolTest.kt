package com.example.agent.tools

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress

class ImageToolTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var tool: GenerateImageTool

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        // El resolver devuelve una IP pública para que UrlGuard permita el servidor de pruebas local
        tool = GenerateImageTool(
            OkHttpClient(), { tmp.root }, server.url("/").toString(),
            resolver = { listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) }, clock = { 42L }
        )
    }

    @After fun tearDown() = server.shutdown()

    private fun args(prompt: String = "a cute cat") = JSONObject().put("prompt", prompt)

    @Test fun savesTheImage_andReturnsItsPath() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(byteArrayOf(9, 8, 7))))
        val r = tool.execute(args().put("width", 640).put("height", 480).put("seed", 7))

        assertEquals(ToolStatus.OK, r.status)
        val path = ImageResult.pathFrom(r.output)
        assertNotNull(path)
        assertEquals("helix-42.jpg", java.io.File(path!!).name)
        assertEquals(3L, java.io.File(path).length())

        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/prompt/a%20cute%20cat?"))
        assertTrue(req.path!!.contains("width=640") && req.path!!.contains("height=480") && req.path!!.contains("seed=7"))
        assertTrue(req.path!!.contains("nologo=true"))
    }

    @Test fun promptWithSlashes_staysASinglePathSegment() {
        val url = tool.buildUrl(args("a/b ?c#d"))
        assertEquals(listOf("prompt", "a%2Fb%20%3Fc%23d"), url.encodedPathSegments)
    }

    @Test fun sizesAreClamped() {
        val url = tool.buildUrl(args().put("width", 99999).put("height", 1))
        assertEquals("1536", url.queryParameter("width"))
        assertEquals("256", url.queryParameter("height"))
    }

    @Test fun nonImageResponse_isAnError() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>"))
        val r = tool.execute(args())
        assertEquals(ToolStatus.ERROR, r.status)
        assertNull(ImageResult.pathFrom(r.output))
    }

    @Test fun httpError_isAnError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(ToolStatus.ERROR, tool.execute(args()).status)
    }

    @Test fun validate_rejectsEmptyOrHugePrompts() {
        listOf("   ", "x".repeat(GenerateImageTool.MAX_PROMPT + 1)).forEach {
            try { tool.validate(args(it)); fail("debía rechazarse") } catch (e: ToolException) { }
        }
        tool.validate(args())
    }

    @Test fun blocksPrivateDestinations() = runBlocking {
        val local = GenerateImageTool(OkHttpClient(), { tmp.root }, "http://127.0.0.1:1")
        try { local.execute(args()); fail("debía bloquearse") } catch (e: ToolException) { }
    }

    @Test fun imageResultParsing() {
        assertEquals("/a/b.jpg", ImageResult.pathFrom("texto\n${ImageResult.marker("/a/b.jpg")}\n"))
        assertNull(ImageResult.pathFrom("sin marcador"))
    }
}
