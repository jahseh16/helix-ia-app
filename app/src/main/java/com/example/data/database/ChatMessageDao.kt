package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun messagesFlow(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun messages(conversationId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): ChatMessageEntity?

    // --- Conversaciones

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun conversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: Long): ConversationEntity?

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touchConversation(id: Long, time: Long)

    @Query("DELETE FROM chat_messages WHERE conversationId = :id")
    suspend fun deleteMessagesOf(id: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationRow(id: Long)

    @Transaction
    suspend fun deleteConversation(id: Long) {
        deleteMessagesOf(id)
        deleteConversationRow(id)
    }

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Transaction
    suspend fun clearEverything() {
        clearAllMessages()
        clearAllConversations()
    }
}
