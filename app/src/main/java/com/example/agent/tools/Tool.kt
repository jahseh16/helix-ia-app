package com.example.agent.tools

import com.example.agent.model.ToolDefinition
import org.json.JSONObject

enum class ToolStatus { OK, ERROR, DENIED }

enum class ToolRisk { READ, WRITE, NETWORK, EXEC }

class ToolResult(val output: String, val status: ToolStatus = ToolStatus.OK) {
    val isError: Boolean get() = status != ToolStatus.OK

    companion object {
        fun ok(output: String) = ToolResult(output, ToolStatus.OK)
        fun error(message: String) = ToolResult(message, ToolStatus.ERROR)
        fun denied(message: String) = ToolResult(message, ToolStatus.DENIED)
    }
}

/** Error esperado de una herramienta (argumentos inválidos, ruta prohibida...). Se devuelve al modelo como resultado. */
open class ToolException(message: String) : Exception(message)

interface Tool {
    val name: String
    val description: String

    /** JSON Schema de los argumentos. */
    val parameters: JSONObject
    val risk: ToolRisk

    /** Resumen legible para la UI y para el diálogo de aprobación. */
    fun summarize(args: JSONObject): String

    /** Valida los argumentos ANTES de pedir permiso. Debe lanzar [ToolException] si son inaceptables. */
    fun validate(args: JSONObject) {}

    suspend fun execute(args: JSONObject): ToolResult
}

class ToolRegistry(tools: List<Tool>) {
    private val byName = tools.associateBy { it.name }

    val all: List<Tool> = tools

    fun get(name: String): Tool? = byName[name]

    fun definitions(): List<ToolDefinition> = all.map { ToolDefinition(it.name, it.description, it.parameters) }
}

const val MAX_OUTPUT_CHARS = 20_000

fun truncateOutput(text: String, max: Int = MAX_OUTPUT_CHARS): String =
    if (text.length <= max) text else text.take(max) + "\n[... salida truncada: ${text.length - max} caracteres más]"

// Helpers para construir JSON Schema y leer argumentos

internal fun prop(type: String, description: String, enum: List<String>? = null): JSONObject =
    JSONObject().put("type", type).put("description", description).also { o ->
        enum?.let { values -> o.put("enum", org.json.JSONArray(values)) }
    }

internal fun schema(required: List<String>, vararg props: Pair<String, JSONObject>): JSONObject =
    JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().also { p -> props.forEach { (k, v) -> p.put(k, v) } })
        put("required", org.json.JSONArray(required))
    }

internal fun JSONObject.requireString(key: String): String {
    val v = if (has(key) && !isNull(key)) opt(key) else null
    val s = (v as? String) ?: throw ToolException("Falta el argumento obligatorio '$key' (string)")
    return s
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) (opt(key) as? String) else null
