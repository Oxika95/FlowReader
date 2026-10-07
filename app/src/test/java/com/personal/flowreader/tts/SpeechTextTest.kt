package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test
    fun separatorsAndBlankAreNotSpeakable() {
        assertFalse(SpeechText.isSpeakable(""))
        assertFalse(SpeechText.isSpeakable("   "))
        assertFalse(SpeechText.isSpeakable("* * *"))
        assertFalse(SpeechText.isSpeakable("—…"))
        assertTrue(SpeechText.isSpeakable("A."))
        assertTrue(SpeechText.isSpeakable("1984"))
        assertTrue(SpeechText.isSpeakable("Ça va"))
    }

    @Test
    fun xmlSafeDropsControlCharsAndLoneSurrogates() {
        assertEquals("plain text", SpeechText.xmlSafe("plain text"))
        assertEquals("ab", SpeechText.xmlSafe("a\u0000\u000Bb"))
        assertEquals("line\nbreak\ttab", SpeechText.xmlSafe("line\nbreak\ttab"))
        assertEquals("ab", SpeechText.xmlSafe("a\uD800b"))
        assertEquals("a\uD83D\uDE00b", SpeechText.xmlSafe("a\uD83D\uDE00b"))
        assertEquals("ab", SpeechText.xmlSafe("a\uFFFEb"))
    }
}
