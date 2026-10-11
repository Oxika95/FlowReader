package com.personal.flowreader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueChaptersTest {
    @Test
    fun singleChapterItemsDoNotExpand() {
        assertTrue(QueueChapters.rows(listOf(0 to "Only"), savedChapter = 0, itemDone = false).isEmpty())
        assertTrue(QueueChapters.rows(emptyList(), savedChapter = 0, itemDone = false).isEmpty())
    }

    @Test
    fun chaptersBeforeSavedAreDone() {
        val rows = QueueChapters.rows(listOf(0 to "A", 2 to "", 5 to "C"), savedChapter = 2, itemDone = false)
        assertEquals(listOf(0, 2, 5), rows.map { it.chapter })
        assertEquals(listOf("A", "Chapter 2", "C"), rows.map { it.label })
        assertEquals(listOf(true, false, false), rows.map { it.done })
    }

    @Test
    fun finishedItemMarksEveryChapterDone() {
        val rows = QueueChapters.rows(listOf(0 to "A", 1 to "B"), savedChapter = 0, itemDone = true)
        assertTrue(rows.all { it.done })
    }
}
