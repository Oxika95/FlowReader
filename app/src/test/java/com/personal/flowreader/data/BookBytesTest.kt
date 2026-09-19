package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookBytesTest {
    @Test
    fun zipMagicIsNotTxt() {
        assertFalse(BookBytes.looksLikeTxt("PK\u0003\u0004".toByteArray()))
    }

    @Test
    fun plainTextIsTxt() {
        assertTrue(BookBytes.looksLikeTxt("Chap".toByteArray()))
    }

    @Test
    fun fileAccessAdviceCoversScopedStorage() {
        val android11 = FileAccessAdvice.forSdk(30)
        assertTrue(android11.contains("Android 11"))
        assertTrue(android11.contains("file picker"))
        val android13 = FileAccessAdvice.forSdk(33)
        assertTrue(android13.contains("Android 13"))
        assertTrue(android13.contains("will not ask"))
    }

    @Test
    fun hashesStreamStable() {
        val a = BookBytes.sha256("hello".byteInputStream())
        val b = BookBytes.sha256("hello".byteInputStream())
        assertEquals(a, b)
        assertEquals(64, a.length)
    }
}
