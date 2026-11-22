package com.example.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetAddress

/** Convención entre la herramienta y la UI: la salida lleva una línea `helix-image:<ruta>`. */
object ImageResult {
    const val TOOL_NAME = "generate_image"
    private const val PREFIX = "helix-image:"

    fun marker(path: String) = "$PREFIX$path"

    fun pathFrom(output: String): String? =
        output.lineSequence().firstOrNull { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.trim()?.ifEmpty { null }
}

/**
 * Genera una imagen con Pollinations AI (gratis, sin API key) y la guarda en [imagesDir].
 * La UI la muestra en el chat con opciones de ver y guardar.
 */
class GenerateImageTool(
    private val client: OkHttpClient,
    private val imagesDir: () -> File,
    private val baseUrl: String = "https://image.pollinations.ai",
    private val resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val clock: () -> Long = System::currentTimeMillis
) : Tool {
    override val name = ImageResult.TOOL_NAME
    override val description =
        "Genera una imagen a partir de una descripción (Pollinations AI, gratis) y se la muestra al usuario en el chat. " +
            "Escribe el prompt en inglés y con detalle (sujeto, estilo, luz, composición) para mejor resultado. " +
            "Después de usarla no repitas rutas ni URLs: la imagen ya es visible."
    override val parameters = schema(
        listOf("prompt"),
        "prompt" to prop("string", "Descripción detallada de la imagen (máx. 800 caracteres)"),
        "width" to prop("integer", "Ancho en píxeles (256-1536, por defecto 1024)"),
        "height" to prop("integer", "Alto en píxeles (256-1536, por defecto 1024)"),
        "seed" to prop("integer", "Semilla opcional para reproducir el resultado")
    )
    override val risk = ToolRisk.NETWORK

    override fun summarize(args: JSONObject) = "Imagen: ${args.optString("prompt").take(120)}"

    override fun validate(args: JSONObject) {
        val prompt = args.requireString("prompt")
        if (prompt.isBlank()) throw ToolException("El prompt de la imagen está vacío")
        if (prompt.length > MAX_PROMPT) throw ToolException("El prompt es demasiado largo (máx. $MAX_PROMPT caracteres)")
    }

    internal fun buildUrl(args: JSONObject): HttpUrl {
        val w = args.optInt("width", 1024).coerceIn(256, 1536)
        val h = args.optInt("height", 1024).coerceIn(256, 1536)
        return baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment("prompt")
            .addPathSegment(args.requireString("prompt").trim())
            .addQueryParameter("width", w.toString())
            .addQueryParameter("height", h.toString())
            .addQueryParameter("nologo", "true")
            .addQueryParameter("model", "flux")
            .apply { if (args.has("seed") && !args.isNull("seed")) addQueryParameter("seed", args.optInt("seed").toString()) }
            .build()
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val url = buildUrl(args)
        withContext(Dispatchers.IO) { UrlGuard.check(url, resolver) }

        val request = Request.Builder().url(url).header("Accept", "image/*").build()
        return try {
            client.newCall(request).executeCancellable { resp ->
                if (!resp.isSuccessful) return@executeCancellable ToolResult.error("Pollinations respondió HTTP ${resp.code}")
                val type = resp.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                if (!type.startsWith("image/")) return@executeCancellable ToolResult.error("La respuesta no es una imagen ($type)")
                val bytes = resp.body?.source()?.let { src ->
                    src.request(MAX_BYTES + 1)
                    if (src.buffer.size > MAX_BYTES) return@executeCancellable ToolResult.error("La imagen supera el tamaño máximo")
                    src.readByteArray()
                } ?: ByteArray(0)
                if (bytes.isEmpty()) return@executeCancellable ToolResult.error("Pollinations devolvió una imagen vacía")

                val dir = imagesDir().also { it.mkdirs() }
                val ext = when (type) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                val file = File(dir, "helix-${clock()}.$ext").also { it.writeBytes(bytes) }
                ToolResult.ok("Imagen generada y mostrada al usuario en el chat.\n${ImageResult.marker(file.path)}")
            }
        } catch (e: java.io.IOException) {
            ToolResult.error("No se pudo descargar la imagen: ${e.message ?: "error de red"}")
        }
    }

    companion object {
        const val MAX_PROMPT = 800
        const val MAX_BYTES = 12L * 1024 * 1024
    }
}
