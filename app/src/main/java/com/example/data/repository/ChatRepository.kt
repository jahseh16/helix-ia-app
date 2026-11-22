package com.example.data.repository

import com.example.data.database.ChatMessageDao
import com.example.data.database.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatMessageDao: ChatMessageDao) {
    val allMessages: Flow<List<ChatMessageEntity>> = chatMessageDao.getAllMessagesFlow()

    suspend fun insertMessage(message: ChatMessageEntity): Long {
        return chatMessageDao.insertMessage(message)
    }

    suspend fun snapshot(): List<ChatMessageEntity> = chatMessageDao.getAllMessages()

    suspend fun clearMessages() {
        chatMessageDao.clearAllMessages()
    }
}
