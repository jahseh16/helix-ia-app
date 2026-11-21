package com.example.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.SecureStore
import com.example.data.security.ValueCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class ReverseCipher : ValueCipher {
    override fun encrypt(plain: ByteArray) = ByteArray(12) + plain.reversedArray()
    override fun decrypt(blob: ByteArray) = blob.copyOfRange(12, blob.size).reversedArray()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatViewModelSecurityTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val legacyPrefs = app.getSharedPreferences("helix_prefs", Context.MODE_PRIVATE)
    private lateinit var store: SecureStore

    @Before
    fun setUp() {
        legacyPrefs.edit().clear().commit()
        val securePrefs = app.getSharedPreferences("vm_test_secure", Context.MODE_PRIVATE)
        securePrefs.edit().clear().commit()
        store = SecureStore(securePrefs, ReverseCipher())
    }

    @Test
    fun legacyPlaintextKeys_areMigratedAndRemovedFromPlainPrefs() {
        legacyPrefs.edit()
            .putString("api_key_openrouter", "or-key")
            .putString("api_key_groq", "groq-key")
            .commit()

        val vm = ChatViewModel(app, store)

        assertEquals("or-key", vm.apiKeyOpenRouter.value)
        assertEquals("groq-key", vm.apiKeyGroq.value)
        assertFalse(legacyPrefs.contains("api_key_openrouter"))
        assertFalse(legacyPrefs.contains("api_key_groq"))
        assertEquals("or-key", store.getString("api_key_openrouter"))
    }

    @Test
    fun updateSettings_neverWritesKeysToPlainPrefs() {
        val vm = ChatViewModel(app, store)
        vm.updateSettings("new-or", "new-groq", "OpenRouter", "m", "ws://127.0.0.1:8080", "p")

        assertFalse(legacyPrefs.contains("api_key_openrouter"))
        assertFalse(legacyPrefs.contains("api_key_groq"))
        assertEquals("new-or", store.getString("api_key_openrouter"))
        assertEquals("new-groq", store.getString("api_key_groq"))
    }
}
