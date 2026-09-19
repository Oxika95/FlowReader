package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextTitleTest {
    @Test
    fun usesFirstNonEmptyLine() {
        assertEquals("Hello world", SharedTextTitle.from("\n\nHello world\nMore"))
    }

    @Test
    fun fallsBackWhenBlank() {
        assertEquals("Shared text", SharedTextTitle.from("   \n  "))
    }

    @Test
    fun truncatesLongLine() {
        val long = "a".repeat(100)
        val title = SharedTextTitle.from(long)
        assertEquals(80, title.length)
        assertEquals('…', title.last())
    }

    @Test
    fun prefersHint() {
        assertEquals("Custom", SharedTextTitle.from("Body", "Custom"))
    }
}
