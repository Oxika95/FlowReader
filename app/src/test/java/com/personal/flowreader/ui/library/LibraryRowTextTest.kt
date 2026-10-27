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

    @Test
    fun chapterChip_kindsAndLabels() {
        assertEquals(ChapterChip("Chapter 0/72", ChapterChipKind.Unstarted), LibraryRowText.chapterChip(72, -1))
        assertEquals(ChapterChip("Chapter 71/72", ChapterChipKind.InProgress), LibraryRowText.chapterChip(72, 70))
        assertEquals(ChapterChip("Chapter 119/119", ChapterChipKind.Complete), LibraryRowText.chapterChip(119, 118))
        assertEquals(ChapterChip("Chapter 10/10", ChapterChipKind.Complete), LibraryRowText.chapterChip(10, 14))
        assertNull(LibraryRowText.chapterChip(0, 0))
    }

    @Test
    fun unreadNew_chaptersAfterSavedPosition() {
        assertEquals(1, LibraryRowText.unreadNew(72, 70)) // 71/72 → 1 new
        assertEquals(2, LibraryRowText.unreadNew(410, 407)) // 408/410 → 2 new
        assertEquals(0, LibraryRowText.unreadNew(119, 118)) // 119/119 → caught up
        assertEquals(0, LibraryRowText.unreadNew(72, -1)) // never started
        assertEquals(0, LibraryRowText.unreadNew(0, 5))
        assertEquals(0, LibraryRowText.unreadNew(10, 14)) // clamped at end
    }
}
