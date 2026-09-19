package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextTitleTest {
    @Test
    fun takesFirst32KeepsAllowedAndSpacesFromBreaks() {
        assertEquals(
            "Hello, world! More text after",
            SharedTextTitle.from("\"\nHello, world!\nMore text after"),
        )
    }

    @Test
    fun lineBreaksBecomeSpaces() {
        assertEquals(
            "Hello world",
            SharedTextTitle.from("Hello\nworld"),
        )
    }

    @Test
    fun dropsDisallowedPunctuationWithoutJoiningWords() {
        assertEquals(
            "Title here.",
            SharedTextTitle.from("# Title here."),
        )
    }

    @Test
    fun fallsBackWhenBlank() {
        assertEquals("Shared text", SharedTextTitle.from("   \n  "))
    }

    @Test
    fun fallsBackWhenOnlyDisallowed() {
        assertEquals("Shared text", SharedTextTitle.from("\"\"\n—\n***"))
    }

    @Test
    fun windowIs32SourceChars() {
        val source = "a".repeat(40)
        assertEquals("a".repeat(32), SharedTextTitle.from(source))
    }

    @Test
    fun prefersHint() {
        assertEquals("Custom", SharedTextTitle.from("Body", "Custom"))
    }
}
