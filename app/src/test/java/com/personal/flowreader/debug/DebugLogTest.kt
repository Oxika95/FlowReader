package com.personal.flowreader.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugLogTest {
    @Before
    fun reset() {
        DebugLog.setMasterEnabled(false)
        DebugLog.setTtsLogEnabled(true)
        DebugLog.setReaderLogEnabled(true)
        DebugLog.clear()
    }

    @Test
    fun dropsWhenMasterOff() {
        DebugLog.setMasterEnabled(false)
        DebugLog.reader("shouldDrop")
        DebugLog.tts("shouldDrop")
        assertTrue(DebugLog.snapshot().isEmpty())
    }

    @Test
    fun keepsWhenChannelOn() {
        DebugLog.setMasterEnabled(true)
        DebugLog.setReaderLogEnabled(true)
        DebugLog.reader("jump si=1")
        val lines = DebugLog.snapshot()
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("reader jump si=1"))
    }

    @Test
    fun dropsWhenChannelOff() {
        DebugLog.setMasterEnabled(true)
        DebugLog.setReaderLogEnabled(false)
        DebugLog.setTtsLogEnabled(false)
        DebugLog.reader("nope")
        DebugLog.tts("nope")
        assertTrue(DebugLog.snapshot().isEmpty())
    }

    @Test
    fun channelsIndependent() {
        DebugLog.setMasterEnabled(true)
        DebugLog.setTtsLogEnabled(true)
        DebugLog.setReaderLogEnabled(false)
        DebugLog.tts("synth")
        DebugLog.reader("jump")
        val lines = DebugLog.snapshot()
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("tts synth"))
    }

    @Test
    fun masterOffClearsRing() {
        DebugLog.setMasterEnabled(true)
        DebugLog.reader("keep")
        assertEquals(1, DebugLog.snapshot().size)
        DebugLog.setMasterEnabled(false)
        assertTrue(DebugLog.snapshot().isEmpty())
    }
}
