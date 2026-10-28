package com.personal.flowreader.plugin.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeAudioFramesTest {
    @Test
    fun payloadOf_headerOnly_returnsNull() {
        assertNull(EdgeAudioFrames.payloadOf(byteArrayOf(0, 0)))
        assertNull(EdgeAudioFrames.payloadOf(byteArrayOf(0, 2, 1, 2)))
    }

    @Test
    fun payloadOf_stripsTwoByteHeader() {
        val frame = byteArrayOf(0, 0, 9, 8, 7)
        assertArrayEquals(byteArrayOf(9, 8, 7), EdgeAudioFrames.payloadOf(frame))
    }

    @Test
    fun payloadOf_stripsNamedHeader() {
        // headerLen = 3, header bytes 65 66 67, then audio
        val frame = byteArrayOf(0, 3, 65, 66, 67, 1, 2, 3, 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), EdgeAudioFrames.payloadOf(frame))
    }

    @Test
    fun assembleMp3_concatenatesPayloads() {
        val a = byteArrayOf(0, 0, 1, 2)
        val b = byteArrayOf(0, 1, 99, 3, 4)
        val out = EdgeAudioFrames.assembleMp3(listOf(a, b))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), out)
        assertEquals(4, out.size)
    }

    @Test
    fun assembleMp3_empty_isEmpty() {
        assertEquals(0, EdgeAudioFrames.assembleMp3(emptyList()).size)
        assertEquals(0, EdgeAudioFrames.assembleMp3(listOf(byteArrayOf(0, 0))).size)
    }
}
