package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsControllerLogicTest {
    @Test fun buttonActions_followThePlayerState() {
        val playing = TtsState(1, TtsPhase.PLAYING)
        assertEquals(TtsAction.PAUSE, nextTtsAction(playing, 1))
        assertEquals(TtsAction.RESUME, nextTtsAction(TtsState(1, TtsPhase.PAUSED), 1))
        assertEquals(TtsAction.STOP, nextTtsAction(TtsState(1, TtsPhase.LOADING), 1))
        assertEquals(TtsAction.START, nextTtsAction(TtsState(1, TtsPhase.ERROR, "x"), 1))
        assertEquals(TtsAction.START, nextTtsAction(TtsState(), 1))
    }

    @Test fun anotherMessagePlaying_doesNotAffectThisOne() {
        assertEquals(TtsAction.START, nextTtsAction(TtsState(1, TtsPhase.PLAYING), 2))
        assertEquals(TtsPhase.IDLE, TtsState(1, TtsPhase.PLAYING).phaseOf(2))
    }

    @Test fun secretsFileKeyHasPriorityOverSettings() {
        assertEquals("FILE", resolveFishKey(" FILE ", "SETTINGS"))
        assertEquals("SETTINGS", resolveFishKey("", "SETTINGS"))
        assertEquals("SETTINGS", resolveFishKey("   ", " SETTINGS "))
        assertEquals("", resolveFishKey("", ""))
    }
}
