package com.example.cloud

import com.example.data.database.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLogicTest {

    @Test
    fun emailValidation() {
        assertNotNull(AuthValidation.emailError(""))
        assertNotNull(AuthValidation.emailError("sin-arroba"))
        assertNull(AuthValidation.emailError("ana@correo.com"))
    }

    @Test
    fun passwordNeedsSixChars() {
        assertNotNull(AuthValidation.passwordError("12345"))
        assertNull(AuthValidation.passwordError("123456"))
    }

    @Test
    fun authErrorsAreInSpanish() {
        assertTrue(AuthErrors.messageForCode("ERROR_EMAIL_ALREADY_IN_USE").contains("ya tiene una cuenta"))
        assertTrue(AuthErrors.messageForCode("ERROR_WRONG_PASSWORD").contains("incorrectos"))
        assertTrue(AuthErrors.messageForCode("ERROR_NETWORK_REQUEST_FAILED").contains("conexión"))
    }

    @Test
    fun imageUrlNeverAcceptsBase64() {
        assertNull(CloudMapping.safeImageUrl("data:image/jpeg;base64,AAAA"))
        assertEquals("https://x/y.jpg", CloudMapping.safeImageUrl("https://x/y.jpg"))
        val msg = ChatMessageEntity(sender = "USER", text = "hola", imageUrl = "data:image/jpeg;base64,AAAA")
        assertFalse(CloudMapping.messageToMap(msg).containsKey("imageUrl"))
    }

    @Test
    fun messageRoundTripKeepsImageUrlAndAnalysis() {
        val msg = ChatMessageEntity(
            sender = "USER", text = "mira", timestamp = 42L, cloudId = "m1",
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/x/o/a.jpg", imageAnalysis = "un gato"
        )
        val back = CloudMapping.messageFromMap("m1", CloudMapping.messageToMap(msg))!!
        assertEquals("m1", back.cloudId)
        assertEquals(msg.imageUrl, back.imageUrl)
        assertEquals("un gato", back.imageAnalysis)
        assertEquals(42L, back.timestamp)
        assertTrue(back.synced)
        assertNull(back.imagePath)
    }

    @Test
    fun hugeTextIsTrimmedForFirestore() {
        val msg = ChatMessageEntity(sender = "TOOL", text = "x".repeat(CloudMapping.MAX_TEXT + 500))
        assertEquals(CloudMapping.MAX_TEXT, (CloudMapping.messageToMap(msg)["text"] as String).length)
    }
}
