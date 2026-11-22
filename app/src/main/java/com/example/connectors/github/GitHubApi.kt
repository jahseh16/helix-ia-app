package com.example.connectors.github

import com.example.agent.tools.executeCancellable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GitHubUser(val login: String, val name: String?, val avatarUrl: String?)

data class RepoInfo(
    val fullName: String,
    val description: String?,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val language: String?,
    val updatedAt: String?
)

data class TreeEntry(val path: String, val isDir: Boolean, val size: Long)
data class TreeResult(val ref: String, val entries: List<TreeEntry>, val truncated: Boolean)
data class CommitInfo(val sha: String, val message: String, val author: String, val date: String)
data class IssueInfo(val number: Int, val title: String, val state: String, val author: String, val isPullRequest: Boolean, val comments: Int, val updatedAt: String)

sealed interface RepoContent {
    data class FileText(val path: String, val size: Long, val text: String) : RepoContent
    data class Binary(val path: String, val size: Long) : RepoContent
    data class Directory(val path: String, val entries: List<TreeEntry>) : RepoContent
}

/** Error de la API de GitHub con mensaje listo para mostrar al usuario / modelo. */
class GitHubException(message: String, val notConnected: Boolean = false) : Exception(message)

/** Cliente mínimo de la API REST de GitHub. El token se pide en cada llamada (no se guarda aquí). */
class GitHubApi(
    private val http: OkHttpClient,
    private val tokenProvider: () -> String?,
    private val baseUrl: String = "https://api.github.com",
    /** Se llama cuando GitHub responde 401: el token se revocó o caducó. */
    private val onUnauthorized: () -> Unit = {}
) {
    suspend fun user(token: String? = null): GitHubUser {
        val o = JSONObject(getText(url("user"), token))
        return GitHubUser(o.optString("login"), o.optString("name").ifEmpty { null }, o.optString("avatar_url").ifEmpty { null })
    }

    suspend fun repos(max: Int = 300): List<RepoInfo> {
        val out = mutableListOf<RepoInfo>()
        var page = 1
        while (out.size < max) {
            val url = url("user", "repos").newBuilder()
                .addQueryParameter("per_page", "100").addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "updated")
                .addQueryParameter("affiliation", "owner,collaborator,organization_member")
                .build()
            val arr = JSONArray(getText(url))
            out += (0 until arr.length()).map { parseRepo(arr.getJSONObject(it)) }
            if (arr.length() < 100) break
            page++
        }
        return out.take(max)
    }

    suspend fun repo(owner: String, name: String): RepoInfo = parseRepo(JSONObject(getText(url("repos", owner, name))))

    suspend fun tree(owner: String, name: String, ref: String?): TreeResult {
        val branch = ref?.takeIf { it.isNotBlank() } ?: repo(owner, name).defaultBranch
        val url = url("repos", owner, name, "git", "trees", branch).newBuilder().addQueryParameter("recursive", "1").build()
        val o = JSONObject(getText(url))
        val arr = o.optJSONArray("tree") ?: JSONArray()
        val entries = (0 until arr.length()).map {
            val e = arr.getJSONObject(it)
            TreeEntry(e.optString("path"), e.optString("type") == "tree", e.optLong("size", 0))
        }
        return TreeResult(branch, entries, o.optBoolean("truncated", false))
    }

    suspend fun contents(owner: String, name: String, path: String, ref: String?): RepoContent {
        val segments = path.split('/').filter { it.isNotEmpty() }
        val builder = url("repos", owner, name, "contents").newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        ref?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("ref", it) }
        val text = getText(builder.build())
        if (text.trimStart().startsWith("[")) {
            val arr = JSONArray(text)
            return RepoContent.Directory(path, (0 until arr.length()).map {
                val e = arr.getJSONObject(it)
                TreeEntry(e.optString("path"), e.optString("type") == "dir", e.optLong("size", 0))
            })
        }
        val o = JSONObject(text)
        val size = o.optLong("size", 0)
        if (o.optString("type") != "file") throw GitHubException("«$path» no es un archivo ni una carpeta (${o.optString("type")}).")
        if (size > MAX_FILE_BYTES) throw GitHubException("«$path» pesa ${size / 1024} KB; el límite para leer es ${MAX_FILE_BYTES / 1024} KB.")
        val bytes = decodeBase64(o.optString("content"))
        return if (bytes.any { it == 0.toByte() }) RepoContent.Binary(path, size)
        else RepoContent.FileText(path, size, String(bytes, Charsets.UTF_8))
    }

    suspend fun commits(owner: String, name: String, limit: Int, path: String?, ref: String?): List<CommitInfo> {
        val b = url("repos", owner, name, "commits").newBuilder().addQueryParameter("per_page", limit.toString())
        path?.takeIf { it.isNotBlank() }?.let { b.addQueryParameter("path", it) }
        ref?.takeIf { it.isNotBlank() }?.let { b.addQueryParameter("sha", it) }
        val arr = JSONArray(getText(b.build()))
        return (0 until arr.length()).map {
            val c = arr.getJSONObject(it)
            val commit = c.optJSONObject("commit")
            val author = commit?.optJSONObject("author")
            CommitInfo(
                sha = c.optString("sha").take(7),
                message = commit?.optString("message").orEmpty().lineSequence().firstOrNull().orEmpty(),
                author = c.optJSONObject("author")?.optString("login")?.takeIf { l -> l.isNotEmpty() } ?: author?.optString("name").orEmpty(),
                date = author?.optString("date").orEmpty()
            )
        }
    }

    suspend fun issues(owner: String, name: String, state: String, limit: Int): List<IssueInfo> {
        val url = url("repos", owner, name, "issues").newBuilder()
            .addQueryParameter("state", state).addQueryParameter("per_page", limit.toString())
            .addQueryParameter("sort", "updated").build()
        val arr = JSONArray(getText(url))
        return (0 until arr.length()).map {
            val i = arr.getJSONObject(it)
            IssueInfo(
                number = i.optInt("number"), title = i.optString("title"), state = i.optString("state"),
                author = i.optJSONObject("user")?.optString("login").orEmpty(),
                isPullRequest = i.has("pull_request"), comments = i.optInt("comments"), updatedAt = i.optString("updated_at")
            )
        }
    }

    // --- Internos

    private fun url(vararg segments: String): HttpUrl =
        baseUrl.toHttpUrl().newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    private fun parseRepo(o: JSONObject) = RepoInfo(
        fullName = o.optString("full_name"),
        description = o.optString("description").takeIf { it.isNotEmpty() && it != "null" },
        isPrivate = o.optBoolean("private"),
        defaultBranch = o.optString("default_branch", "main"),
        language = o.optString("language").takeIf { it.isNotEmpty() && it != "null" },
        updatedAt = o.optString("updated_at").takeIf { it.isNotEmpty() }
    )

    private suspend fun getText(url: HttpUrl, tokenOverride: String? = null): String {
        val token = tokenOverride ?: tokenProvider()
        if (token.isNullOrBlank()) throw GitHubException("GitHub no está conectado.", notConnected = true)
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HelixAgent")
            .get().build()
        return try {
            http.newCall(request).executeCancellable { r ->
                val body = r.body?.string().orEmpty()
                if (r.isSuccessful) return@executeCancellable body
                throw errorFor(r.code, r.header("x-ratelimit-remaining"), r.header("x-ratelimit-reset"), body)
            }
        } catch (e: GitHubException) {
            if (e.notConnected) onUnauthorized()
            throw e
        } catch (e: IOException) {
            throw GitHubException("No se pudo conectar con GitHub: ${e.message ?: "error de red"}")
        }
    }

    companion object {
        const val MAX_FILE_BYTES = 400_000L

        fun decodeBase64(s: String): ByteArray = android.util.Base64.decode(s.replace("\n", ""), android.util.Base64.DEFAULT)

        fun errorFor(code: Int, remaining: String?, reset: String?, body: String): GitHubException {
            val apiMessage = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
            return when {
                code == 401 -> GitHubException("El acceso a GitHub caducó o fue revocado. Vuelve a conectar GitHub en Conectores.", notConnected = true)
                (code == 403 || code == 429) && (remaining == "0" || apiMessage.contains("rate limit", true)) -> {
                    val at = reset?.toLongOrNull()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it * 1000)) }
                    GitHubException("Límite de peticiones de GitHub alcanzado" + (at?.let { " (se restablece a las $it)" } ?: "") + ". No reintentes ahora.")
                }
                code == 403 -> GitHubException("GitHub denegó el acceso (HTTP 403)" + (if (apiMessage.isNotEmpty()) ": $apiMessage" else "") + ".")
                code == 404 -> GitHubException("No existe o no tienes acceso (HTTP 404). Revisa el nombre del repositorio, la rama y la ruta.")
                code == 409 -> GitHubException("El repositorio está vacío (HTTP 409).")
                code == 422 -> GitHubException("GitHub rechazó la petición (HTTP 422)" + (if (apiMessage.isNotEmpty()) ": $apiMessage" else "") + ".")
                code in 500..599 -> GitHubException("GitHub tiene problemas (HTTP $code). Prueba de nuevo en un momento.")
                else -> GitHubException("GitHub respondió HTTP $code" + (if (apiMessage.isNotEmpty()) ": $apiMessage" else "") + ".")
            }
        }
    }
}
