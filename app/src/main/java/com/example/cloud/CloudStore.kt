package com.example.cloud

import android.net.Uri
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ConversationEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Acceso a Firebase con la estructura acordada:
 *  - Firestore: `users/{uid}/chats/{chatId}` y `users/{uid}/chats/{chatId}/messages/{messageId}`
 *  - Storage:   `users/{uid}/images/{chatId}/{messageId}.jpg`
 */
class CloudStore(private val db: FirebaseFirestore, private val storage: FirebaseStorage) {

    private fun chats(uid: String) = db.collection("users").document(uid).collection("chats")

    private fun imageRef(uid: String, chatId: String, messageId: String) =
        storage.reference.child("users/$uid/images/$chatId/$messageId.jpg")

    suspend fun upsertChat(uid: String, chat: ConversationEntity) {
        chats(uid).document(chat.cloudId ?: return).set(CloudMapping.chatToMap(chat)).await()
    }

    suspend fun upsertMessage(uid: String, chatId: String, message: ChatMessageEntity) {
        chats(uid).document(chatId).collection("messages").document(message.cloudId ?: return)
            .set(CloudMapping.messageToMap(message)).await()
    }

    suspend fun listChats(uid: String): List<CloudChat> =
        chats(uid).get().await().documents.map { CloudMapping.chatFromMap(it.id, it.data.orEmpty()) }

    suspend fun listMessages(uid: String, chatId: String): List<ChatMessageEntity> =
        chats(uid).document(chatId).collection("messages").orderBy("timestamp").get().await()
            .documents.mapNotNull { CloudMapping.messageFromMap(it.id, it.data.orEmpty()) }

    /** Sube el JPEG ya comprimido (binario, no base64) y devuelve su URL de descarga. */
    suspend fun uploadImage(uid: String, chatId: String, messageId: String, file: File): String {
        val ref = imageRef(uid, chatId, messageId)
        ref.putFile(Uri.fromFile(file), StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun downloadImage(uid: String, chatId: String, messageId: String, dest: File) {
        dest.parentFile?.mkdirs()
        imageRef(uid, chatId, messageId).getFile(dest).await()
    }

    /** Borra el chat, sus mensajes y sus imágenes. Las imágenes que ya no existan se ignoran. */
    suspend fun deleteChat(uid: String, chatId: String) {
        val chat = chats(uid).document(chatId)
        val messages = chat.collection("messages").get().await().documents
        messages.chunked(400).forEach { part ->
            val batch = db.batch()
            part.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        chat.delete().await()
        messages.filter { !(it.getString("imageUrl")).isNullOrBlank() }.forEach {
            runCatching { imageRef(uid, chatId, it.id).delete().await() }
        }
    }
}
