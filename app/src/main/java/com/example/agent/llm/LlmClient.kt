package com.example.agent.llm

import com.example.agent.model.LlmRequest
import com.example.agent.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface LlmClient {
    /** Emite los eventos del turno. Cancelar la colección aborta la petición HTTP. */
    fun stream(request: LlmRequest): Flow<StreamEvent>
}

/** Respuesta HTTP no exitosa del proveedor. */
class LlmApiException(val code: Int, val detail: String) : Exception("HTTP $code: $detail") {
    /** Heurística: el modelo/endpoint rechazó el campo `tools` (muchos modelos gratuitos no lo soportan). */
    val suggestsToolsUnsupported: Boolean
        get() = code in setOf(400, 404, 422) && detail.contains("tool", ignoreCase = true)
}

/** Error enviado por el proveedor dentro del propio stream SSE. */
class LlmStreamException(message: String) : Exception(message)
