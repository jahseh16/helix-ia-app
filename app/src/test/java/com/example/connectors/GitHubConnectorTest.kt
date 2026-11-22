package com.example.connectors

import com.example.agent.tools.ToolException
import com.example.agent.tools.ToolStatus
import com.example.connectors.github.DeviceFlowClient
import com.example.connectors.github.DeviceFlowException
import com.example.connectors.github.GitHubApi
import com.example.connectors.github.GitHubListReposTool
import com.example.connectors.github.GitHubTreeTool
import com.example.connectors.github.GitHubCommitsTool
import com.example.connectors.github.GitHubIssuesTool
import com.example.connectors.github.parseRepoRef
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GitHubConnectorTest {
    private lateinit var server: MockWebServer
    private val slept = mutableListOf<Long>()
    private var clock = 0L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() = server.shutdown()

    private fun flow() = DeviceFlowClient(OkHttpClient(), server.url("/").toString(), sleep = { slept += it; clock += it }, now = { clock })
    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private val deviceBody = """{"device_code":"DEV123","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""

    @Test fun requestsDeviceCode_withClientIdAndScopes() = runBlocking {
        server.enqueue(json(deviceBody))
        val code = flow().requestDeviceCode("CID", "repo read:user")
        assertEquals("ABCD-1234", code.userCode)
        assertEquals("https://github.com/login/device", code.verificationUri)
        assertEquals(5, code.intervalSeconds)
        val req = server.takeRequest()
        assertEquals("/login/device/code", req.path)
        assertEquals("application/json", req.getHeader("Accept"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("client_id=CID") && body.contains("scope=repo+read%3Auser") || body.contains("scope=repo%20read%3Auser"))
    }

    @Test fun unknownClientId_givesAClearMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Not Found"}"""))
        try { flow().requestDeviceCode("X", "repo"); fail() } catch (e: DeviceFlowException) {
            assertTrue(e.message!!.contains("Client ID"))
        }
    }

    @Test fun polling_respectsInterval_slowDown_andReturnsToken() = runBlocking {
        server.enqueue(json(deviceBody))
        server.enqueue(json("""{"error":"authorization_pending"}"""))
        server.enqueue(json("""{"error":"slow_down","interval":10}"""))
        server.enqueue(json("""{"access_token":"gho_abc","token_type":"bearer"}"""))
        val f = flow()
        val code = f.requestDeviceCode("CID", "repo")
        val token = f.pollForToken("CID", code)
        assertEquals("gho_abc", token)
        assertEquals(listOf(5000L, 5000L, 10000L), slept)
        server.takeRequest()
        val poll = server.takeRequest().body.readUtf8()
        assertTrue(poll.contains("device_code=DEV123") && poll.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
    }

    @Test fun polling_stopsOnDeniedAndExpired() = runBlocking {
        for ((error, fragment) in listOf("access_denied" to "Cancelaste", "expired_token" to "caducó", "device_flow_disabled" to "Device Flow")) {
            server.enqueue(json("""{"error":"$error"}"""))
            val code = com.example.connectors.github.DeviceCode("D", "U", "https://x", 900, 5)
            try { flow().pollForToken("CID", code); fail(error) } catch (e: DeviceFlowException) {
                assertTrue("'${e.message}' debía contener '$fragment'", e.message!!.contains(fragment))
            }
        }
    }

    @Test fun polling_givesUpWhenTheCodeExpires() = runBlocking {
        server.enqueue(json("""{"error":"authorization_pending"}"""))
        val code = com.example.connectors.github.DeviceCode("D", "U", "https://x", 3, 5)
        try { flow().pollForToken("CID", code); fail() } catch (e: DeviceFlowException) {
            assertTrue(e.message!!.contains("caducó"))
        }
    }

    // --- Referencias a repos y herramientas

    @Test fun parseRepoRef_acceptsCommonForms() {
        listOf("octo/cat", "@octo/cat", "/octo/cat", "https://github.com/octo/cat", "github.com/octo/cat.git", "octo/cat/").forEach {
            assertEquals(it, "octo/cat", parseRepoRef(it).fullName)
        }
    }

    @Test fun parseRepoRef_rejectsBadInput() {
        listOf("", "solo", "../x", "a/..", "a b/c", "a/b c", "owner/").forEach {
            try { parseRepoRef(it); fail("'$it' debía rechazarse") } catch (e: ToolException) { }
        }
    }

    private fun api(token: String? = "T", onUnauthorized: () -> Unit = {}) =
        GitHubApi(OkHttpClient(), { token }, server.url("/").toString(), onUnauthorized)

    @Test fun listRepos_sendsAuth_andFilters() = runBlocking {
        server.enqueue(json("""[{"full_name":"octo/cat","private":true,"default_branch":"main","description":"Gatos","language":"Kotlin"},
            {"full_name":"octo/dog","private":false,"default_branch":"dev","description":null}]"""))
        val r = GitHubListReposTool(api()).execute(JSONObject().put("query", "cat"))
        assertEquals(ToolStatus.OK, r.status)
        assertTrue(r.output.contains("octo/cat") && !r.output.contains("octo/dog"))
        val req = server.takeRequest()
        assertEquals("Bearer T", req.getHeader("Authorization"))
        assertTrue(req.path!!.startsWith("/user/repos?"))
    }

    @Test fun notConnected_flagsTheConnectorWithoutCallingGitHub() = runBlocking {
        val r = GitHubListReposTool(api(token = null)).execute(JSONObject())
        assertEquals(ToolStatus.ERROR, r.status)
        assertEquals("github", r.connectorRequired)
        assertEquals(0, server.requestCount)
    }

    @Test fun unauthorized_clearsTheSession_andAsksToReconnect() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}"""))
        var revoked = false
        val r = GitHubListReposTool(api(onUnauthorized = { revoked = true })).execute(JSONObject())
        assertTrue(revoked)
        assertEquals("github", r.connectorRequired)
    }

    @Test fun tree_usesDefaultBranch_andFiltersByPath() = runBlocking {
        server.enqueue(json("""{"full_name":"octo/cat","default_branch":"main","private":false}"""))
        server.enqueue(json("""{"truncated":false,"tree":[{"path":"README.md","type":"blob","size":10},{"path":"src","type":"tree"},{"path":"src/A.kt","type":"blob","size":99}]}"""))
        val r = GitHubTreeTool(api()).execute(JSONObject().put("repo", "octo/cat").put("path", "src"))
        assertTrue(r.output.contains("src/A.kt") && !r.output.contains("README.md"))
        server.takeRequest()
        assertEquals("/repos/octo/cat/git/trees/main?recursive=1", server.takeRequest().path)
    }

    @Test fun commitsAndIssues_formatResults() = runBlocking {
        server.enqueue(json("""[{"sha":"abcdef123456","commit":{"message":"Arregla bug\n\ndetalle","author":{"name":"Ana","date":"2026-11-01T10:00:00Z"}},"author":{"login":"ana"}}]"""))
        val c = GitHubCommitsTool(api()).execute(JSONObject().put("repo", "octo/cat"))
        assertTrue(c.output.contains("abcdef1  2026-11-01  ana: Arregla bug"))
        server.enqueue(json("""[{"number":7,"title":"Falla","state":"open","user":{"login":"bob"},"comments":2,"updated_at":"2026-11-02T00:00:00Z","pull_request":{}}]"""))
        val i = GitHubIssuesTool(api()).execute(JSONObject().put("repo", "octo/cat"))
        assertTrue(i.output.contains("#7 [PR open] Falla — @bob"))
    }

    @Test fun rateLimitAndNotFound_haveClearMessages() {
        assertTrue(GitHubApi.errorFor(403, "0", "1795355136", "{}").message!!.contains("Límite"))
        assertTrue(GitHubApi.errorFor(404, null, null, "{}").message!!.contains("404"))
        assertFalse(GitHubApi.errorFor(404, null, null, "{}").notConnected)
        assertTrue(GitHubApi.errorFor(401, null, null, "{}").notConnected)
    }
}
