package com.example.ui

import androidx.compose.ui.graphics.Color

/** Marca/proveedor de un modelo: color de acento y, si no hay icono vectorial, una inicial. */
enum class Brand(val label: String, val color: Color, val initial: String) {
    NVIDIA("NVIDIA", Color(0xFF76B900), "N"),
    BAIDU("Baidu", Color(0xFF4E6EF2), "B"),
    GOOGLE("Google", Color(0xFF4C8DF6), "G"),
    PERPLEXITY("Perplexity", Color(0xFF20B8CD), "P"),
    LIQUID("Liquid AI", Color(0xFFB49CFF), "L"),
    OPENROUTER("OpenRouter", Color(0xFF8B8DF7), "R"),
    AUTO("Automático", Color(0xFF64D2FF), "A"),
    META("Meta", Color(0xFF3D8BFF), "M"),
    OPENAI("OpenAI", Color(0xFFE4F3F5), "O"),
    QWEN("Qwen", Color(0xFF8B87FF), "Q"),
    PARTICLE("Particle", Color(0xFFFF8FB1), "P"),
    INCLUSION("inclusionAI", Color(0xFFFFB340), "i"),
    RECURSIVE("Recursive", Color(0xFF5EE0B5), "R"),
    GITHUB("GitHub", Color(0xFFF2F2F7), "G"),
    GENERIC("IA", Color(0xFF8D97B0), "·")
}

/**
 * Un modelo seleccionable. [id] es el identificador técnico que viaja en la petición HTTP y NUNCA se
 * muestra al usuario: la interfaz usa [name] y el logo de [brand].
 * [tools]: `true` si se verificó tool calling nativo, `false` si solo chat, `null` si no se ha probado.
 */
data class AiModel(val id: String, val name: String, val brand: Brand, val tools: Boolean? = null)

object ModelCatalog {
    val providers = listOf("OpenRouter", "Groq")

    const val DEFAULT_OPENROUTER = "openrouter/free"

    /**
     * Solo modelos que respondieron HTTP 200 con texto válido en una prueba real contra la API
     * (chat, streaming SSE y tool calling). Se excluyen embeddings, rerank, audio, imagen y
     * "decisions", que no sirven para chat.
     */
    val openRouter = listOf(
        AiModel("openrouter/free", "OpenRouter Free", Brand.AUTO, tools = true),
        AiModel("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra 550B", Brand.NVIDIA, tools = true),
        AiModel("nvidia/nemotron-3.5-super-vl:free", "Nemotron 3.5 Super VL", Brand.NVIDIA, tools = true),
        AiModel("nvidia/nemotron-3.5-lightning:free", "Nemotron 3.5 Lightning", Brand.NVIDIA, tools = true),
        AiModel("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "Nemotron 3 Nano Omni 30B", Brand.NVIDIA, tools = true),
        AiModel("baidu/ernie-5.5-flash-preview:free", "ERNIE 5.5 Flash", Brand.BAIDU, tools = true),
        AiModel("liquid/lfm-3-1.7b:free", "LFM 3 1.7B", Brand.LIQUID, tools = true)
    )

    /** Groq: sin verificar contra la API en esta revisión (por eso `tools = null`). */
    val groq = listOf(
        AiModel("llama-3.3-70b-versatile", "Llama 3.3 70B", Brand.META),
        AiModel("llama-3.1-8b-instant", "Llama 3.1 8B Instant", Brand.META),
        AiModel("openai/gpt-oss-120b", "GPT OSS 120B", Brand.OPENAI),
        AiModel("openai/gpt-oss-20b", "GPT OSS 20B", Brand.OPENAI),
        AiModel("qwen/qwen3-32b", "Qwen 3 32B", Brand.QWEN)
    )

    /** IDs de OpenRouter que estuvieron en la lista y ya no existen o no sirven; se migran al modelo por defecto. */
    val retiredIds = setOf(
        "cohere/north-mini-code:free",
        "nvidia/nemotron-3.5-content-safety:free",
        "poolside/laguna-m.1:free",
        "openai/gpt-oss-20b:free",
        "poolside/laguna-xs-2.1:free"
    )

    fun modelsFor(provider: String): List<AiModel> = if (provider == "Groq") groq else openRouter

    /** Modelo de la lista, o uno sintetizado (nombre legible + marca deducida) para IDs personalizados. */
    fun resolve(provider: String, id: String): AiModel =
        modelsFor(provider).firstOrNull { it.id == id } ?: AiModel(id, prettyName(id), brandOfId(id))

    /** Interpreta el texto guardado en cada mensaje ("Proveedor · id", o el formato antiguo con " - "). */
    fun resolveStored(raw: String?): AiModel? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(" · ", " - ", limit = 2)
        if (parts.size < 2) return null // texto libre ("Pensando", "Asistente"...), no un modelo
        return resolve(parts[0].trim(), parts[1].trim())
    }

    /** "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free" → "Nemotron 3 Nano Omni 30B A3B Reasoning". */
    fun prettyName(id: String): String =
        id.substringAfterLast('/').removeSuffix(":free").split('-', '_', ' ').filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                when {
                    token.first().isDigit() -> token.uppercase()
                    else -> token.replaceFirstChar { it.uppercase() }
                }
            }

    fun brandOfId(id: String): Brand = when (id.substringBefore('/', "").lowercase()) {
        "nvidia" -> Brand.NVIDIA
        "baidu" -> Brand.BAIDU
        "google" -> Brand.GOOGLE
        "perplexity" -> Brand.PERPLEXITY
        "liquid" -> Brand.LIQUID
        "openrouter" -> Brand.AUTO
        "openai" -> Brand.OPENAI
        "meta-llama", "meta" -> Brand.META
        "qwen" -> Brand.QWEN
        "particle-ai" -> Brand.PARTICLE
        "inclusionai" -> Brand.INCLUSION
        "recursive" -> Brand.RECURSIVE
        else -> if (id.startsWith("llama")) Brand.META else Brand.GENERIC
    }
}
