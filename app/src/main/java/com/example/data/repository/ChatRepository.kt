package com.example.data.repository

import com.example.data.database.ChatMessageDao
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ConversationEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatMessageDao) {
    val conversations: Flow<List<ConversationEntity>> = dao.conversationsFlow()

    fun messages(conversationId: Long): Flow<List<ChatMessageEntity>> = dao.messagesFlow(conversationId)

    suspend fun snapshot(conversationId: Long): List<ChatMessageEntity> = dao.messages(conversationId)

    /** Guarda el mensaje en su conversación y la sube al principio del historial. */
    suspend fun insertMessage(conversationId: Long, message: ChatMessageEntity): Long {
        val id = dao.insertMessage(message.copy(conversationId = conversationId))
        dao.touchConversation(conversationId, System.currentTimeMillis())
        return id
    }

    suspend fun createConversation(firstMessage: String): Long =
        dao.insertConversation(ConversationEntity(title = ConversationEntity.titleFrom(firstMessage)))

    suspend fun exists(conversationId: Long): Boolean = dao.conversation(conversationId) != null

    suspend fun deleteConversation(conversationId: Long) = dao.deleteConversation(conversationId)

    suspend fun deleteAll() = dao.clearEverything()
}
