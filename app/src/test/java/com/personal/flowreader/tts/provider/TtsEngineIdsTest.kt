package com.personal.flowreader.tts.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEngineIdsTest {
    @Test
    fun pluginKeyRoundTrip() {
        assertEquals("tts:edgetts", TtsEngineIds.plugin("edgetts"))
        assertTrue(TtsEngineIds.isPlugin("tts:edgetts"))
        assertEquals("edgetts", TtsEngineIds.pluginIdOf("tts:edgetts"))
        assertFalse(TtsEngineIds.isPlugin("system"))
        assertNull(TtsEngineIds.pluginIdOf("system"))
    }

    @Test
    fun legacyEdgeConstantMatchesMigrationSource() {
        assertEquals("edge", TtsEngineIds.LEGACY_EDGE)
    }
}
