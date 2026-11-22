package com.example.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Una conversación guardada del historial (panel lateral). */
@Entity(tableName = "conversations", indices = [Index("cloudId")])
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Id del documento `users/{uid}/chats/{chatId}`. */
    val cloudId: String? = UUID.randomUUID().toString(),
    /** Cuenta propietaria en la nube; null = chat solo local (se adopta al iniciar sesión). */
    val ownerUid: String? = null
) {
    companion object {
        const val MAX_TITLE = 48

        /** Título a partir del primer mensaje: una línea, sin Markdown y recortado por palabra. */
        fun titleFrom(firstMessage: String): String {
            val line = firstMessage.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                .replace(Regex("[#*_`>~\\[\\]]"), "").replace(Regex("\\s+"), " ").trim()
            if (line.isEmpty()) return "Nuevo chat"
            if (line.length <= MAX_TITLE) return line
            val cut = line.take(MAX_TITLE).substringBeforeLast(' ').ifBlank { line.take(MAX_TITLE) }
            return cut.trimEnd(',', '.', ';', ':') + "…"
        }
    }
}
