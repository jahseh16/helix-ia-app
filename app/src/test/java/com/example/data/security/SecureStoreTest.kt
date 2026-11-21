package com.example.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cifrador reversible determinista, solo para tests (el real exige Android Keystore). */
private class FakeCipher(var failEncrypt: Boolean = false, var failDecrypt: Boolean = false) : ValueCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        if (failEncrypt) error("encrypt boom")
        return ByteArray(12) + plain.reversedArray()
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (failDecrypt) error("decrypt boom")
        return blob.copyOfRange(12, blob.size).reversedArray()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("test_secure", Context.MODE_PRIVATE)
    private val cipher = FakeCipher()
    private lateinit var store: SecureStore

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        store = SecureStore(prefs, cipher)
    }

    @Test
    fun roundTrip_returnsOriginalValue() {
        store.putString("api_key", "sk-or-v1-secret")
        assertEquals("sk-or-v1-secret", store.getString("api_key"))
    }

    @Test
    fun valueOnDisk_isNotPlaintext() {
        store.putString("api_key", "sk-or-v1-secret")
        val raw = prefs.all.values.joinToString()
        assertFalse("La key no debe aparecer en claro", raw.contains("sk-or-v1-secret"))
    }

    @Test
    fun missingKey_returnsNull() {
        assertNull(store.getString("nope"))
    }

    @Test
    fun emptyValue_removesEntry() {
        store.putString("api_key", "abc")
        store.putString("api_key", "")
        assertNull(store.getString("api_key"))
        assertEquals(0, prefs.all.size)
    }

    @Test
    fun undecryptableValue_returnsNullInsteadOfThrowing() {
        store.putString("api_key", "abc")
        cipher.failDecrypt = true
        assertNull(store.getString("api_key"))
    }

    @Test
    fun encryptFailure_throwsAndNeverStoresPlaintext() {
        cipher.failEncrypt = true
        assertThrows(SecureStoreException::class.java) { store.putString("api_key", "abc") }
        assertEquals(0, prefs.all.size)
    }
}
