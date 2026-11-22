package com.example.cloud

import com.example.data.database.ChatMessageEntity
import com.example.data.database.ConversationEntity

/** Chat tal como está en `users/{uid}/chats/{chatId}`. */
data class CloudChat(val id: String, val title: String, val createdAt: Long, val updatedAt: Long)

/**
 * Traducción entre filas de Room y documentos de Firestore. Aquí vive la regla de oro de las imágenes:
 * en Firestore solo entra la URL https de Storage; jamás base64 ni data URIs.
 */
object CloudMapping {
    /** Un documento de Firestore admite 1 MiB; las salidas enormes de herramientas se recortan. */
    const val MAX_TEXT = 100_000

    fun chatToMap(c: ConversationEntity): Map<String, Any> = mapOf(
        "title" to c.title,
        "createdAt" to c.createdAt,
        "updatedAt" to c.updatedAt
    )

    fun chatFromMap(id: String, d: Map<String, Any?>): CloudChat = CloudChat(
        id = id,
        title = d["title"] as? String ?: "Chat",
        createdAt = (d["createdAt"] as? Number)?.toLong() ?: 0L,
        updatedAt = (d["updatedAt"] as? Number)?.toLong() ?: 0L
    )

    /** Solo URLs http(s): descarta `data:` (base64) y cualquier otra cosa. */
    fun safeImageUrl(url: String?): String? = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    fun messageToMap(m: ChatMessageEntity): Map<String, Any> = buildMap {
        put("sender", m.sender)
        put("text", m.text.take(MAX_TEXT))
        put("timestamp", m.timestamp)
        m.modelUsed?.let { put("modelUsed", it) }
        m.toolCallsJson?.let { put("toolCallsJson", it.take(MAX_TEXT)) }
        m.reasoning?.let { put("reasoning", it.take(MAX_TEXT)) }
        m.toolCallId?.let { put("toolCallId", it) }
        m.toolName?.let { put("toolName", it) }
        m.toolArgs?.let { put("toolArgs", it.take(MAX_TEXT)) }
        m.toolStatus?.let { put("toolStatus", it) }
        safeImageUrl(m.imageUrl)?.let { put("imageUrl", it) }
        m.imageAnalysis?.let { put("imageAnalysis", it.take(MAX_TEXT)) }
    }

    fun messageFromMap(id: String, d: Map<String, Any?>): ChatMessageEntity? {
        val sender = d["sender"] as? String ?: return null
        return ChatMessageEntity(
            sender = sender,
            text = d["text"] as? String ?: "",
            timestamp = (d["timestamp"] as? Number)?.toLong() ?: 0L,
            modelUsed = d["modelUsed"] as? String,
            toolCallsJson = d["toolCallsJson"] as? String,
            reasoning = d["reasoning"] as? String,
            toolCallId = d["toolCallId"] as? String,
            toolName = d["toolName"] as? String,
            toolArgs = d["toolArgs"] as? String,
            toolStatus = d["toolStatus"] as? String,
            cloudId = id,
            imageUrl = safeImageUrl(d["imageUrl"] as? String),
            imageAnalysis = d["imageAnalysis"] as? String,
            synced = true
        )
    }
}
