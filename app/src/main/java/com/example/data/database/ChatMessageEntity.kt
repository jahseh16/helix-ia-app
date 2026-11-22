package com.example.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una fila de la conversación. Según [sender]:
 *  - USER:   [text] es lo que escribió el usuario.
 *  - AI:     [text] es la respuesta; [toolCallsJson] las herramientas que pidió; [reasoning] su razonamiento.
 *  - TOOL:   resultado de una herramienta ([toolCallId], [toolName], [toolArgs], [toolStatus]); [text] es la salida.
 *  - SYSTEM: aviso informativo de la interfaz.  ERROR: fallo mostrado al usuario.
 */
@Entity(tableName = "chat_messages", indices = [Index("conversationId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val toolCallsJson: String? = null,
    val reasoning: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolStatus: String? = null,
    /** Conversación a la que pertenece (tabla `conversations`). */
    @ColumnInfo(defaultValue = "0") val conversationId: Long = 0
) {
    companion object {
        const val SENDER_USER = "USER"
        const val SENDER_AI = "AI"
        const val SENDER_TOOL = "TOOL"
        const val SENDER_SYSTEM = "SYSTEM"
        const val SENDER_ERROR = "ERROR"
    }
}
