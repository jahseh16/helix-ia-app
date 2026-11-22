package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun messagesFlow(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
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

    // --- Sincronización con la nube

    @Query("SELECT * FROM conversations WHERE cloudId = :cloudId LIMIT 1")
    suspend fun conversationByCloudId(cloudId: String): ConversationEntity?

    @Query("SELECT * FROM conversations")
    suspend fun allConversations(): List<ConversationEntity>

    @Query("UPDATE conversations SET ownerUid = :uid WHERE ownerUid IS NULL")
    suspend fun adoptUnownedConversations(uid: String)

    @Query("UPDATE conversations SET ownerUid = :uid WHERE id = :id")
    suspend fun setConversationOwner(id: Long, uid: String)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationMeta(id: Long, title: String, updatedAt: Long)

    @Query("SELECT cloudId FROM chat_messages WHERE conversationId = :conversationId AND cloudId IS NOT NULL")
    suspend fun cloudIdsOf(conversationId: Long): List<String>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId AND synced = 1")
    suspend fun syncedCount(conversationId: Long): Int

    @Query(
        "SELECT m.* FROM chat_messages m JOIN conversations c ON c.id = m.conversationId " +
            "WHERE m.synced = 0 AND c.ownerUid = :uid ORDER BY m.id ASC"
    )
    suspend fun unsyncedMessages(uid: String): List<ChatMessageEntity>

    @Query(
        "SELECT m.* FROM chat_messages m JOIN conversations c ON c.id = m.conversationId " +
            "WHERE c.ownerUid = :uid AND m.imageUrl IS NOT NULL AND m.imagePath IS NULL"
    )
    suspend fun messagesMissingImage(uid: String): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("UPDATE chat_messages SET imageUrl = :url WHERE id = :id")
    suspend fun setImageUrl(id: Long, url: String)

    @Query("UPDATE chat_messages SET imagePath = :path WHERE id = :id")
    suspend fun setImagePath(id: Long, path: String)

    @Query("SELECT imagePath FROM chat_messages WHERE conversationId = :conversationId AND imagePath IS NOT NULL")
    suspend fun imagePathsOf(conversationId: Long): List<String>

    @Query("SELECT imagePath FROM chat_messages WHERE imagePath IS NOT NULL")
    suspend fun allImagePaths(): List<String>

    @Query("SELECT * FROM conversations WHERE ownerUid IS NOT NULL")
    suspend fun ownedConversations(): List<ConversationEntity>

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
