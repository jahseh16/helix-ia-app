package com.example.cloud

import android.util.Log
import com.example.data.database.ChatMessageDao
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ConversationEntity
import com.example.data.repository.ChatSyncHook
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class SyncState(val syncing: Boolean = false, val lastSyncAt: Long? = null, val error: String? = null)

/**
 * Sincroniza Room con Firestore/Storage mientras hay sesión iniciada:
 *  - cada mensaje guardado se sube (y su imagen se sube a Storage; en Firestore solo va la URL);
 *  - al iniciar sesión o abrir la app autenticado se bajan los chats, mensajes e imágenes que falten;
 *  - lo que no se pudo subir (sin red) queda con `synced = 0` y se reintenta en la siguiente sincronización.
 * Los chats creados sin sesión se "adoptan" por la cuenta al entrar. Al cerrar sesión se retiran del
 * teléfono los chats de la cuenta (siguen en la nube) para no mezclarlos con otra cuenta.
 */
class CloudSync(
    private val dao: ChatMessageDao,
    private val store: CloudStore?,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
    private val imageDir: File
) : ChatSyncHook {

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    /** Todo el trabajo contra la nube va en serie: así un mensaje nunca se sube a medias dos veces. */
    private val lock = Mutex()

    init {
        scope.launch {
            auth.user.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) syncNow()
            }
        }
    }

    override fun currentUid(): String? = auth.currentUid()

    override fun onMessageSaved(messageId: Long) {
        val uid = auth.currentUid() ?: return
        val s = store ?: return
        scope.launch {
            try {
                lock.withLock { pushMessage(s, uid, messageId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo subir el mensaje $messageId", e)
            }
        }
    }

    override fun onConversationDeleted(cloudId: String?, ownerUid: String?) {
        val uid = auth.currentUid() ?: return
        val s = store ?: return
        if (cloudId == null || ownerUid != uid) return
        scope.launch {
            try {
                lock.withLock { s.deleteChat(uid, cloudId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo borrar el chat en la nube", e)
            }
        }
    }

    /** Sincronización completa: bajar lo nuevo de la nube y subir lo pendiente. */
    suspend fun syncNow() {
        val uid = auth.currentUid() ?: return
        val s = store ?: run {
            _state.value = SyncState(error = "Firebase no está disponible.")
            return
        }
        lock.withLock {
            _state.update { it.copy(syncing = true, error = null) }
            try {
                dao.adoptUnownedConversations(uid)
                pull(s, uid)
                downloadMissingImages(s, uid)
                dao.unsyncedMessages(uid).forEach { pushMessage(s, uid, it.id) }
                _state.value = SyncState(lastSyncAt = System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sincronización fallida", e)
                _state.update { it.copy(syncing = false, error = friendly(e)) }
            }
        }
    }

    /**
     * Antes de cerrar sesión: intenta subir lo pendiente y devuelve cuántos mensajes siguen sin subir.
     * Si es 0 (o [force]), retira del teléfono los chats de la cuenta.
     */
    suspend fun prepareSignOut(force: Boolean): Int {
        val uid = auth.currentUid() ?: return 0
        return lock.withLock {
            store?.let { s ->
                withTimeoutOrNull(FLUSH_TIMEOUT_MS) { dao.unsyncedMessages(uid).forEach { pushMessage(s, uid, it.id) } }
            }
            val pending = dao.unsyncedMessages(uid).size
            if (pending == 0 || force) {
                dao.ownedConversations().filter { it.ownerUid == uid }.forEach { removeLocal(it) }
                _state.value = SyncState()
                0
            } else pending
        }
    }

    // --- Bajar

    private suspend fun pull(s: CloudStore, uid: String) {
        val remote = s.listChats(uid)
        val remoteIds = remote.map { it.id }.toSet()

        // Un chat que ya se había subido y ya no está en la nube se borró desde otro dispositivo
        dao.ownedConversations()
            .filter { it.ownerUid == uid && it.cloudId !in remoteIds && dao.syncedCount(it.id) > 0 }
            .forEach { removeLocal(it) }

        for (chat in remote) {
            val local = dao.conversationByCloudId(chat.id)
            val convId = if (local == null) {
                dao.insertConversation(
                    ConversationEntity(title = chat.title, createdAt = chat.createdAt, updatedAt = chat.updatedAt, cloudId = chat.id, ownerUid = uid)
                )
            } else {
                if (chat.updatedAt <= local.updatedAt) continue
                local.id
            }
            val have = dao.cloudIdsOf(convId).toSet()
            for (m in s.listMessages(uid, chat.id)) {
                val messageId = m.cloudId ?: continue
                if (messageId in have) continue
                val rowId = dao.insertMessage(m.copy(conversationId = convId, synced = true))
                if (m.imageUrl != null) downloadImage(s, uid, chat.id, messageId, rowId)
            }
            if (local != null) dao.updateConversationMeta(local.id, chat.title, chat.updatedAt)
        }
    }

    private suspend fun downloadMissingImages(s: CloudStore, uid: String) {
        for (m in dao.messagesMissingImage(uid)) {
            val chatId = dao.conversation(m.conversationId)?.cloudId ?: continue
            downloadImage(s, uid, chatId, m.cloudId ?: continue, m.id)
        }
    }

    private suspend fun downloadImage(s: CloudStore, uid: String, chatId: String, messageId: String, rowId: Long) {
        try {
            val dest = File(imageDir, "$messageId.jpg")
            s.downloadImage(uid, chatId, messageId, dest)
            dao.setImagePath(rowId, dest.absolutePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo bajar la imagen $messageId", e) // se reintenta en la próxima sincronización
        }
    }

    // --- Subir

    private suspend fun pushMessage(s: CloudStore, uid: String, id: Long) {
        var msg = dao.getMessageById(id) ?: return
        val conv = dao.conversation(msg.conversationId) ?: return
        when (conv.ownerUid) {
            null -> dao.setConversationOwner(conv.id, uid)
            uid -> Unit
            else -> return // chat de otra cuenta: nunca se sube a esta
        }
        val chatId = conv.cloudId ?: return
        val messageId = msg.cloudId ?: return

        var imageOk = true
        val path = msg.imagePath
        if (path != null && msg.imageUrl == null) {
            val file = File(path)
            if (file.isFile) {
                val url = try {
                    withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { s.uploadImage(uid, chatId, messageId, file) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo subir la imagen", e)
                    null
                }
                if (url != null) {
                    dao.setImageUrl(id, url)
                    msg = msg.copy(imageUrl = url)
                } else imageOk = false
            }
        }

        val written = withTimeoutOrNull(PUSH_TIMEOUT_MS) {
            s.upsertChat(uid, conv.copy(ownerUid = uid))
            s.upsertMessage(uid, chatId, msg)
            true
        } ?: false
        if (written && imageOk) dao.markSynced(id)
    }

    private suspend fun removeLocal(conv: ConversationEntity) {
        dao.imagePathsOf(conv.id).forEach { runCatching { File(it).delete() } }
        dao.deleteConversation(conv.id)
    }

    private fun friendly(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firestore rechazó el acceso. Comprueba que la base de datos esté creada en Firebase y que las reglas (firestore.rules) estén publicadas."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND ->
            "No existe la base de datos de Firestore. Créala en la consola de Firebase."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Sin conexión con Firebase. Se reintentará al sincronizar de nuevo."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "No se pudo sincronizar."
    }

    private companion object {
        const val TAG = "HelixCloudSync"
        const val UPLOAD_TIMEOUT_MS = 45_000L
        const val PUSH_TIMEOUT_MS = 20_000L
        const val FLUSH_TIMEOUT_MS = 20_000L
    }
}
