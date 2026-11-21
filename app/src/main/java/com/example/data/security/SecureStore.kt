package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra y descifra bytes. Separado de [SecureStore] para poder sustituirlo en tests. */
interface ValueCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM con una clave no exportable en Android Keystore.
 * El blob resultante es `iv (12 bytes) || ciphertext+tag`.
 * La clave nunca sale del hardware/TEE y no se incluye en backups.
 */
class KeystoreValueCipher(private val alias: String = DEFAULT_ALIAS) : ValueCipher {

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE) { "Blob cifrado inválido" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE)
        )
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    companion object {
        const val DEFAULT_ALIAS = "helix_secure_store_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}

/**
 * Almacén clave/valor para secretos (API keys). Los valores se guardan cifrados;
 * en disco solo hay Base64 de `iv || ciphertext`.
 *
 * - [getString] nunca lanza: si no se puede descifrar (clave perdida, datos corruptos,
 *   Keystore no disponible) devuelve `null` y el usuario simplemente vuelve a introducir la key.
 * - [putString] lanza [SecureStoreException] si no se puede cifrar. Nunca cae a texto plano.
 */
class SecureStore(
    private val prefs: SharedPreferences,
    private val cipher: ValueCipher
) {
    fun getString(name: String): String? {
        val stored = prefs.getString(PREFIX + name, null) ?: return null
        return try {
            String(cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun putString(name: String, value: String) {
        if (value.isEmpty()) {
            remove(name)
            return
        }
        val encoded = try {
            Base64.encodeToString(cipher.encrypt(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecureStoreException("No se pudo cifrar el valor en el almacén seguro", e)
        }
        prefs.edit().putString(PREFIX + name, encoded).apply()
    }

    fun remove(name: String) {
        prefs.edit().remove(PREFIX + name).apply()
    }

    companion object {
        private const val PREFIX = "enc_"
        const val PREFS_FILE = "helix_secure_prefs"

        fun create(context: Context): SecureStore = SecureStore(
            prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
            cipher = KeystoreValueCipher()
        )
    }
}

class SecureStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
