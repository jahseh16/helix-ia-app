package com.example.connectors

import kotlinx.coroutines.flow.StateFlow

/** Cuenta conectada a un servicio (lo que la UI muestra en la tarjeta del conector). */
data class ConnectorAccount(
    val login: String,
    val displayName: String? = null,
    /** Foto descargada en local, o null. */
    val avatarPath: String? = null
)

sealed interface ConnectorState {
    data object Disconnected : ConnectorState

    /** Autorización en curso. [userCode] es null mientras se pide el código a la API. */
    data class Connecting(val userCode: String? = null, val verificationUri: String? = null) : ConnectorState

    data class Connected(val account: ConnectorAccount) : ConnectorState

    /** El último intento de conectar falló; la tarjeta muestra el motivo y permite reintentar. */
    data class Failed(val message: String) : ConnectorState
}

/**
 * Integración con un servicio externo. Para añadir otro (Google Drive, Notion...) basta con implementar
 * esta interfaz y añadirla a [ConnectorRegistry]: la pantalla de Conectores se genera sola.
 */
interface Connector {
    val id: String
    val title: String
    val description: String

    /** Clave del logo (la UI la traduce a su icono): "github"... */
    val iconKey: String
    val state: StateFlow<ConnectorState>

    /** Inicia la conexión. [onUserCode] recibe el código que el usuario debe introducir en el navegador. */
    fun connect(onUserCode: (String) -> Unit = {})

    /** Cancela una conexión en curso. */
    fun cancel()

    /** Cierra la sesión y borra las credenciales guardadas. */
    fun disconnect()
}

class ConnectorRegistry(val all: List<Connector>) {
    fun get(id: String): Connector? = all.firstOrNull { it.id == id }
}
