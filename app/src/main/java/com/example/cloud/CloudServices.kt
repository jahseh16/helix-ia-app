package com.example.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * Instancias de Firebase obtenidas de forma tolerante: si Firebase no está inicializado (p. ej. sin
 * google-services.json o en un test JVM) devuelven null y la app sigue funcionando solo en local.
 */
object CloudServices {
    fun auth(): FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    fun store(): CloudStore? = runCatching { CloudStore(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance()) }.getOrNull()
}
