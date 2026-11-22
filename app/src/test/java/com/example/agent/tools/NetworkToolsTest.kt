package com.example.agent.tools

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class NetworkToolsTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

    // El MockWebServer escucha en localhost: el resolver de test simula un host público
    private val publicResolver: (String) -> List<InetAddress> = { listOf(InetAddress.getByName("93.184.216.34")) }

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun blocked(url: String) {
        try {
            UrlGuard.check(url.toHttpUrl())
            fail("$url debía bloquearse")
        } catch (e: ToolException) { /* esperado */ }
    }

    // --- UrlGuard (SSRF)
    @Test fun urlGuard_blocksLoopbackPrivateAndMetadataAddresses() {
        blocked("http://127.0.0.1/")
        blocked("http://localhost:8080/")
        blocked("http://10.0.0.5/")
        blocked("http://192.168.1.1/admin")
        blocked("http://172.16.0.1/")
        blocked("http://169.254.169.254/latest/meta-data")
        blocked("http://100.64.0.1/")
        blocked("http://[::1]/")
        blocked("http://[fd00::1]/")
        blocked("http://0.0.0.0/")
    }

    @Test fun urlGuard_allowsPublicAddresses() {
        UrlGuard.check("https://example.com/".toHttpUrl()) { listOf(InetAddress.getByName("93.184.216.34")) }
    }

    @Test fun urlGuard_blocksHostThatResolvesToAPrivateAddressAmongOthers() {
        try {
            UrlGuard.check("https://rebind.test/".toHttpUrl()) {
                listOf(InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.1"))
            }
            fail("debía bloquearse")
        } catch (e: ToolException) { /* esperado */ }
    }

    @Test fun tools_rejectNonHttpSchemes() {
        listOf("file:///etc/passwd", "ftp://x.com/a", "javascript:alert(1)").forEach { u ->
            try {
                HttpRequestTool(client).validate(JSONObject().put("url", u))
                fail("$u debía rechazarse")
            } catch (e: ToolException) { /* esperado */ }
        }
    }

    // --- http_request
    @Test fun httpRequest_getReturnsStatusHeadersAndBody() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"ok":true}"""))
        val r = runBlocking {
            HttpRequestTool(client, publicResolver).execute(JSONObject().put("url", server.url("/api").toString()))
        }
        assertEquals(ToolStatus.OK, r.status)
        assertTrue(r.output.contains("HTTP 200"))
        assertTrue(r.output.contains("""{"ok":true}"""))
        assertTrue(r.output.contains("NO CONFIABLE"))
    }

    @Test fun httpRequest_postSendsBodyAndHeaders() {
        server.enqueue(MockResponse().setBody("ok"))
        runBlocking {
            HttpRequestTool(client, publicResolver).execute(
                JSONObject("""{"url":"${server.url("/x")}","method":"POST","body":"{\"a\":1}","headers":{"X-Test":"1","Host":"evil.com"}}""")
            )
        }
        val req = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", req.method)
        assertEquals("{\"a\":1}", req.body.readUtf8())
        assertEquals("1", req.getHeader("X-Test"))
        assertTrue("el header Host del modelo debe ignorarse", req.getHeader("Host") != "evil.com")
    }

    @Test fun httpRequest_errorStatusIsAnErrorResult() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val r = runBlocking { HttpRequestTool(client, publicResolver).execute(JSONObject().put("url", server.url("/").toString())) }
        assertEquals(ToolStatus.ERROR, r.status)
        assertTrue(r.output.contains("HTTP 500"))
    }

    @Test fun httpRequest_blocksLocalTargetsEndToEnd() {
        val r = runBlocking { HttpRequestTool(client).execute(JSONObject().put("url", server.url("/").toString())) }
        assertEquals(ToolStatus.ERROR, r.status)
        assertTrue(r.output.contains("bloqueado"))
        assertEquals(0, server.requestCount)
    }

    @Test fun httpRequest_followsRedirects_butRevalidatesEveryHop() {
        // Primer salto público (simulado) que redirige a una IP privada: el segundo salto debe bloquearse
        val resolver: (String) -> List<InetAddress> = { host ->
            listOf(InetAddress.getByName(if (host == "169.254.169.254") "169.254.169.254" else "93.184.216.34"))
        }
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://169.254.169.254/latest/meta-data"))
        val r = runBlocking { HttpRequestTool(client, resolver).execute(JSONObject().put("url", server.url("/start").toString())) }
        assertEquals(ToolStatus.ERROR, r.status)
        assertTrue(r.output.contains("bloqueado"))
        assertEquals(1, server.requestCount)
    }

    @Test fun httpRequest_followsAnOrdinaryRedirect() {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/final"))
        server.enqueue(MockResponse().setBody("destino"))
        val r = runBlocking { HttpRequestTool(client, publicResolver).execute(JSONObject().put("url", server.url("/inicio").toString())) }
        assertEquals(ToolStatus.OK, r.status)
        assertTrue(r.output.contains("destino"))
    }

    // --- web_fetch
    @Test fun webFetch_convertsHtmlToReadableText() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(
                "<html><head><title>Mi &amp; página</title><style>p{color:red}</style></head>" +
                    "<body><script>alert(1)</script><h1>Hola</h1><p>Texto&nbsp;uno</p><p>Dos &lt;3</p></body></html>"
            )
        )
        val r = runBlocking { WebFetchTool(client, publicResolver).execute(JSONObject().put("url", server.url("/").toString())) }
        assertEquals(ToolStatus.OK, r.status)
        assertTrue(r.output.contains("Título: Mi & página"))
        assertTrue(r.output.contains("Hola"))
        assertTrue(r.output.contains("Texto uno"))
        assertTrue(r.output.contains("Dos <3"))
        assertTrue(!r.output.contains("alert(1)") && !r.output.contains("color:red"))
    }

    @Test fun webFetch_rejectsBinaryContent_andHttpErrors() {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("xx"))
        assertEquals(ToolStatus.ERROR, runBlocking { WebFetchTool(client, publicResolver).execute(JSONObject().put("url", server.url("/").toString())) }.status)
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(runBlocking { WebFetchTool(client, publicResolver).execute(JSONObject().put("url", server.url("/").toString())) }.output.contains("404"))
    }

    // --- HtmlText
    @Test fun htmlText_handlesNumericEntitiesAndWhitespace() {
        val (title, text) = HtmlText.extract("<title> T </title><div>a&#65;b</div>\n\n\n\n<p>   c   d  </p>")
        assertEquals("T", title)
        assertEquals("aAb\n\nc d", text)
    }
}
