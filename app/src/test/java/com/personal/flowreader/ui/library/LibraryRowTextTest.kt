package com.personal.flowreader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryRowTextTest {
    @Test
    fun readStoryShowsChapterOfTotal() {
        assertEquals("Ann · chapter 40/55", LibraryRowText.subtitle("Ann", 55, 39))
    }

    @Test
    fun unreadStoryShowsChapterCount() {
        assertEquals("Ann · 55 chapters", LibraryRowText.subtitle("Ann", 55, -1))
        assertEquals("Ann · 1 chapter", LibraryRowText.subtitle("Ann", 1, -1))
    }

    @Test
    fun missingAuthorOrCount() {
        assertEquals("Chapter 3/10", LibraryRowText.subtitle("", 10, 2))
        assertEquals("10 chapters", LibraryRowText.subtitle("", 10, -1))
        assertEquals("Ann", LibraryRowText.subtitle("Ann", 0, 4))
        assertNull(LibraryRowText.subtitle("", 0, -1))
    }

    @Test
    fun savedChapterPastTheEndIsClamped() {
        assertEquals("Ann · chapter 10/10", LibraryRowText.subtitle("Ann", 10, 14))
    }
}
