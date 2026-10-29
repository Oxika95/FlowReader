package com.personal.flowreader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRemainderMatchTest {
    @Test
    fun matchingPathKeepsRemainder() {
        assertTrue(
            TtsAudioEngine.pendingMatchesExpected("/cache/s1000075.mp3", "/cache/s1000075.mp3"),
        )
    }

    @Test
    fun otherSentenceDropsRemainder() {
        assertFalse(
            TtsAudioEngine.pendingMatchesExpected("/cache/s1000075.mp3", "/cache/s1000076.mp3"),
        )
    }

    @Test
    fun emptyPendingDecodesFile() {
        assertFalse(TtsAudioEngine.pendingMatchesExpected(null, "/cache/s1000075.mp3"))
    }
}
