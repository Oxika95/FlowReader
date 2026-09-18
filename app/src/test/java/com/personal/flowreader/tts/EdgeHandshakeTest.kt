package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeHandshakeTest {
    @Test
    fun gecIsUpperHex64() {
        val gec = EdgeHandshake.secMsGec(1_700_000_000L)
        assertEquals(64, gec.length)
        assertTrue(gec.matches(Regex("[0-9A-F]+")))
    }

    @Test
    fun gecStableForSameFiveMinuteWindow() {
        val a = EdgeHandshake.secMsGec(1_700_000_010L)
        val b = EdgeHandshake.secMsGec(1_700_000_080L)
        assertEquals(a, b)
    }
}
