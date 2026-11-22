package com.example.agent.tools

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class WebSearchToolTest {
    private lateinit var ddg: MockWebServer
    private lateinit var wiki: MockWebServer
    private lateinit var tool: WebSearchTool

    private val ddgHtml = """
        <div class="result"><h2><a rel="nofollow" class="result__a" href="https://www.gaming.net/es/juegos/">10 mejores juegos &amp; más</a></h2>
        <a class="result__snippet" href="x">Los <b>juegos</b> de supervivencia</a></div>
        <div class="result"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa%3Fb%3D1&rut=z">Ejemplo</a>
        <a class="result__snippet" href="x">Otro extracto</a></div>
        <div class="result"><a class="result__a" href="https://duckduckgo.com/y.js?ad=1">Anuncio</a></div>
    """.trimIndent()

    @Before fun setUp() {
        ddg = MockWebServer().also { it.start() }
        wiki = MockWebServer().also { it.start() }
        tool = WebSearchTool(
            OkHttpClient(), ddg.url("/html/").toString(), wiki.url("/w/api.php").toString(),
            resolver = { listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) }
        )
    }

    @After fun tearDown() { ddg.shutdown(); wiki.shutdown() }

    @Test fun parsesDuckDuckGoResults_decodingRedirects_andSkippingAds() {
        val hits = DuckDuckGoParser.parse(ddgHtml, 10)
        assertEquals(2, hits.size)
        assertEquals("10 mejores juegos & más", hits[0].title)
        assertEquals("Los juegos de supervivencia", hits[0].snippet)
        assertEquals("https://example.com/a?b=1", hits[1].url)
    }

    @Test fun searchUsesPostAndReturnsRealUrls() = runBlocking {
        ddg.enqueue(MockResponse().setBody(ddgHtml))
        val r = tool.execute(JSONObject().put("query", "juegos supervivencia"))
        assertEquals(ToolStatus.OK, r.status)
        assertTrue(r.output.contains("https://www.gaming.net/es/juegos/"))
        assertTrue(r.output.contains("web_fetch"))
        val req = ddg.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.body.readUtf8().contains("q=juegos%20supervivencia"))
    }

    @Test fun antiBotChallenge_fallsBackToWikipedia() = runBlocking {
        ddg.enqueue(MockResponse().setResponseCode(202).setBody("anomaly"))
        wiki.enqueue(MockResponse().setBody("""{"query":{"search":[{"title":"Videojuego de supervivencia","snippet":"Los <span>juegos</span> de"}]}}"""))
        val r = tool.execute(JSONObject().put("query", "juegos supervivencia"))
        assertEquals(ToolStatus.OK, r.status)
        assertTrue(r.output.contains("Wikipedia"))
        assertTrue(r.output.contains("/wiki/Videojuego_de_supervivencia"))
        assertTrue(r.output.contains("Los juegos de"))
    }

    @Test fun rateLimitEverywhere_isAClearError() = runBlocking {
        ddg.enqueue(MockResponse().setResponseCode(429))
        wiki.enqueue(MockResponse().setResponseCode(429))
        val r = tool.execute(JSONObject().put("query", "x"))
        assertEquals(ToolStatus.ERROR, r.status)
        assertTrue(r.output.contains("429"))
    }

    @Test fun emptyQueryIsRejected() {
        try { tool.validate(JSONObject().put("query", "  ")); fail() } catch (e: ToolException) { }
    }

    @Test fun httpErrorHints_guideTheModel() {
        assertTrue(httpErrorHint(404, "https://a.com/x").contains("NO la reintentes"))
        assertTrue(httpErrorHint(429, "https://a.com/x").contains("a.com"))
        assertTrue(httpErrorHint(503, "https://a.com/x").contains("otra fuente"))
    }

    @Test fun searchEnginePages_areDetected() {
        assertTrue(isSearchEnginePage("https://www.google.com/search?q=x".toHttpUrl()))
        assertTrue(isSearchEnginePage("https://www.google.com.pe/search?q=x".toHttpUrl()))
        assertTrue(isSearchEnginePage("https://www.bing.com/search?q=x".toHttpUrl()))
        assertFalse(isSearchEnginePage("https://developer.android.com/search-guide".toHttpUrl()))
        assertFalse(isSearchEnginePage("https://www.google.com/maps".toHttpUrl()))
    }

    @Test fun webFetch_refusesGoogleResultPages() {
        try {
            WebFetchTool(OkHttpClient()).validate(JSONObject().put("url", "https://www.google.com/search?q=juegos"))
            fail("debía rechazarse")
        } catch (e: ToolException) {
            assertTrue(e.message!!.contains("web_search"))
        }
    }
}
