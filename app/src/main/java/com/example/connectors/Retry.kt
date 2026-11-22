package com.example.connectors

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Reintenta ante fallos de red (`IOException`, incluido `UnknownHostException`) con espera creciente corta:
 * 0,6 s → 1,2 s → 2,4 s. Al volver del navegador el teléfono puede tardar un par de segundos en recuperar la
 * red, y sin esto la primera petición falla con «Unable to resolve host». Solo se reintenta lo que lanza
 * `IOException`; cualquier otro error (HTTP 4xx, JSON...) se propaga al instante.
 */
suspend fun <T> retryOnNetworkError(
    attempts: Int = 3,
    baseDelayMs: Long = 600,
    sleep: suspend (Long) -> Unit = { delay(it) },
    block: suspend () -> T
): T {
    var last: IOException? = null
    for (i in 0 until attempts) {
        try {
            return block()
        } catch (e: IOException) {
            last = e
            if (i < attempts - 1) sleep(baseDelayMs shl i)
        }
    }
    throw last ?: IOException("Error de red")
}
