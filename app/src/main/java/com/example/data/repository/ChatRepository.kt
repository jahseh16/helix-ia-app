package com.example.data.repository

import com.example.data.database.ChatMessageDao
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ConversationEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Avisos del repositorio a la capa de sincronización con la nube (opcional: sin sesión no hace nada). */
interface ChatSyncHook {
    /** Uid de la cuenta con sesión iniciada, o null si no hay. */
    fun currentUid(): String?
    fun onMessageSaved(messageId: Long)
    fun onConversationDeleted(cloudId: String?, ownerUid: String?)
}

class ChatRepository(private val dao: ChatMessageDao, var sync: ChatSyncHook? = null) {
    val conversations: Flow<List<ConversationEntity>> = dao.conversationsFlow()

    fun messages(conversationId: Long): Flow<List<ChatMessageEntity>> = dao.messagesFlow(conversationId)

    suspend fun snapshot(conversationId: Long): List<ChatMessageEntity> = dao.messages(conversationId)

    /** Guarda el mensaje en su conversación y la sube al principio del historial. */
    suspend fun insertMessage(conversationId: Long, message: ChatMessageEntity): Long {
        val id = dao.insertMessage(message.copy(conversationId = conversationId, synced = false))
        dao.touchConversation(conversationId, System.currentTimeMillis())
        sync?.onMessageSaved(id)
        return id
    }

    /** Reescribe un mensaje ya guardado (p. ej. al añadirle el análisis de la imagen) y lo marca para sincronizar. */
    suspend fun updateMessage(message: ChatMessageEntity) {
        dao.insertMessage(message.copy(synced = false))
        dao.touchConversation(message.conversationId, System.currentTimeMillis())
        sync?.onMessageSaved(message.id)
    }

    suspend fun message(id: Long): ChatMessageEntity? = dao.getMessageById(id)

    suspend fun createConversation(firstMessage: String): Long =
        dao.insertConversation(
            ConversationEntity(title = ConversationEntity.titleFrom(firstMessage), ownerUid = sync?.currentUid())
        )

    suspend fun exists(conversationId: Long): Boolean = dao.conversation(conversationId) != null

    suspend fun deleteConversation(conversationId: Long) {
        val conv = dao.conversation(conversationId)
        dao.imagePathsOf(conversationId).forEach { runCatching { File(it).delete() } }
        dao.deleteConversation(conversationId)
        if (conv != null) sync?.onConversationDeleted(conv.cloudId, conv.ownerUid)
    }

    suspend fun deleteAll() {
        val all = dao.allConversations()
        dao.allImagePaths().forEach { runCatching { File(it).delete() } }
        dao.clearEverything()
        all.forEach { sync?.onConversationDeleted(it.cloudId, it.ownerUid) }
    }
}
