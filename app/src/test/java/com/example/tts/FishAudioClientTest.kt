package com.example.tts

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FishAudioClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: FishAudioClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = FishAudioClient(OkHttpClient(), server.url("/").toString())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun sendsExpectedRequest_andReturnsAudioBytes() = runBlocking {
        val audio = byteArrayOf(1, 2, 3, 4)
        server.enqueue(MockResponse().setBody(Buffer().write(audio)).setHeader("Content-Type", "audio/mpeg"))

        val out = client.synthesize("Hola", "KEY123", TtsVoices.KURISU.id)

        assertArrayEquals(audio, out)
        val req = server.takeRequest()
        assertEquals("/v1/tts", req.path)
        assertEquals("POST", req.method)
        assertEquals("Bearer KEY123", req.getHeader("Authorization"))
        assertEquals("s2.1-pro-free", req.getHeader("model"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("Hola", body.getString("text"))
        assertEquals("mp3", body.getString("format"))
        assertEquals(TtsVoices.KURISU.id, body.getString("reference_id"))
    }

    @Test fun defaultVoiceIsTheTenderAnimeOne() {
        assertEquals("60525c84e0a142db9af4932408eca0cc", TtsVoices.default.id)
        assertEquals(TtsVoices.ANIME, TtsVoices.byId("desconocida"))
        assertEquals(TtsVoices.SPANISH, TtsVoices.byId("2116540cce1e462cbbcc1a4225ba46cc"))
    }

    @Test fun missingKey_failsWithoutCallingTheApi() = runBlocking {
        try {
            client.synthesize("Hola", "  ")
            fail("debía fallar")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("API key"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun httpErrors_mapToFriendlyMessages() = runBlocking {
        for ((code, fragment) in listOf(401 to "no es válida", 402 to "saldo", 429 to "Límite", 500 to "HTTP 500")) {
            server.enqueue(MockResponse().setResponseCode(code))
            try {
                client.synthesize("Hola", "K")
                fail("debía fallar con $code")
            } catch (e: TtsException) {
                assertTrue("'${e.message}' debía contener '$fragment'", e.message!!.contains(fragment))
            }
        }
    }

    @Test fun emptyAudio_isAnError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        try {
            client.synthesize("Hola", "K")
            fail("debía fallar")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("vacío"))
        }
    }
}
