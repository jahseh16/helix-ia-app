package com.example.data.profile

import android.content.ContentResolver
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import java.io.File

data class UserProfile(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    /** Ruta local de la foto (copia privada de la app), o null. */
    val photoPath: String? = null
) {
    val displayName: String get() = name.ifBlank { username.ifBlank { "Tu perfil" } }

    val initials: String
        get() = name.ifBlank { username }.split(' ', '_', '.').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }

    companion object {
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
        private val USERNAME = Regex("^[A-Za-z0-9._]{2,30}$")

        /** Normaliza el nombre de usuario: sin `@` inicial ni espacios. */
        fun cleanUsername(raw: String): String = raw.trim().removePrefix("@").replace(" ", "")

        /** Mensaje de error por campo (null = válido). Los campos vacíos son válidos: todo es opcional. */
        fun usernameError(raw: String): String? {
            val u = cleanUsername(raw)
            return if (u.isEmpty() || USERNAME.matches(u)) null else "Solo letras, números, punto y guion bajo (2-30)"
        }

        fun emailError(raw: String): String? {
            val e = raw.trim()
            return if (e.isEmpty() || EMAIL.matches(e)) null else "Correo no válido"
        }
    }
}

/** Perfil guardado en local (SharedPreferences + foto en el almacenamiento privado de la app). */
class ProfileStore(private val prefs: SharedPreferences, private val dir: File) {

    fun load(): UserProfile = UserProfile(
        name = prefs.getString(KEY_NAME, "").orEmpty(),
        username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        email = prefs.getString(KEY_EMAIL, "").orEmpty(),
        photoPath = prefs.getString(KEY_PHOTO, null)?.takeIf { File(it).isFile }
    )

    val isSetupDone: Boolean get() = prefs.getBoolean(KEY_DONE, false)

    fun save(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.name.trim())
            .putString(KEY_USERNAME, UserProfile.cleanUsername(profile.username))
            .putString(KEY_EMAIL, profile.email.trim())
            .putString(KEY_PHOTO, profile.photoPath)
            .putBoolean(KEY_DONE, true)
            .apply()
    }

    fun markSetupDone() = prefs.edit().putBoolean(KEY_DONE, true).apply()

    /**
     * Copia la foto elegida a la carpeta privada de la app, girada según EXIF, recortada en cuadrado
     * y reducida a 512 px. El nombre cambia en cada guardado para que la UI no muestre una versión en caché.
     */
    fun importPhoto(resolver: ContentResolver, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        val rotated = if (rotation != 0) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } else decoded

        val side = minOf(rotated.width, rotated.height)
        val square = Bitmap.createBitmap(rotated, (rotated.width - side) / 2, (rotated.height - side) / 2, side, side)
        val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square

        dir.mkdirs()
        dir.listFiles()?.filter { it.name.startsWith("avatar") }?.forEach { it.delete() }
        val out = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out.path
    }.getOrNull()

    companion object {
        const val KEY_NAME = "profile_name"
        const val KEY_USERNAME = "profile_username"
        const val KEY_EMAIL = "profile_email"
        const val KEY_PHOTO = "profile_photo"
        const val KEY_DONE = "profile_setup_done"
        val KEYS = setOf(KEY_NAME, KEY_USERNAME, KEY_EMAIL, KEY_PHOTO, KEY_DONE)
        private const val SIZE = 512
    }
}
