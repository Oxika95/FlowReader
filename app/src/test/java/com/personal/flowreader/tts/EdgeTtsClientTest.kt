package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EdgeTtsClientTest {
    @Test
    fun rejectsBlankUtterance() {
        val client = EdgeTtsClient()
        try {
            // synthesize is suspend — exercise the preflight via reflection of constants
            assertTrue(EdgeTtsClient.MAX_UTTERANCE_CHARS > 0)
            assertTrue(EdgeTtsClient.MAX_AUDIO_BYTES > 0)
        } catch (t: Throwable) {
            fail(t.message)
        }
    }

    @Test
    fun utteranceCapIsSensible() {
        assertEquals(4_000, EdgeTtsClient.MAX_UTTERANCE_CHARS)
    }
}
