package com.personal.flowreader.ui.library

import com.personal.flowreader.data.BookItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    private fun book(
        id: String,
        title: String,
        author: String = "",
        addedAt: Long = 0L,
        chapterIndex: Int = -1,
        chapterCount: Int = 0,
    ) = BookItem(
        bookId = id,
        title = title,
        storedPath = "",
        sourceUri = "",
        sourceKind = "Imported",
        readingProgress = 0f,
        lastReadAt = addedAt,
        addedAt = addedAt,
        chapterIndex = chapterIndex,
        author = author,
        chapterCount = chapterCount,
    )

    @Test
    fun titleAlphabetical() {
        val sorted = LibrarySort.sort(
            listOf(book("2", "zeta"), book("1", "Alpha"), book("3", "beta")),
            LibrarySortMode.Title,
        )
        assertEquals(listOf("1", "3", "2"), sorted.map { it.bookId })
    }

    @Test
    fun authorBlankLastThenTitle() {
        val sorted = LibrarySort.sort(
            listOf(
                book("a", "Zed", author = ""),
                book("b", "Ann", author = "Bee"),
                book("c", "Bob", author = "Aye"),
            ),
            LibrarySortMode.Author,
        )
        assertEquals(listOf("c", "b", "a"), sorted.map { it.bookId })
    }

    @Test
    fun dateAddedNewestFirst() {
        val sorted = LibrarySort.sort(
            listOf(
                book("old", "Old", addedAt = 1),
                book("new", "New", addedAt = 3),
                book("mid", "Mid", addedAt = 2),
            ),
            LibrarySortMode.DateAdded,
        )
        assertEquals(listOf("new", "mid", "old"), sorted.map { it.bookId })
    }

    @Test
    fun updates_unreadAheadThenUnstartedThenComplete_alphaWithin() {
        // Unread-ahead (incl. mid-progress) → unstarted → complete; title A–Z inside each band.
        val sorted = LibrarySort.sort(
            listOf(
                book("done-b", "Done B", chapterIndex = 9, chapterCount = 10),
                book("new-b", "New B", chapterIndex = 70, chapterCount = 72),
                book("mid-a", "Mid A", chapterIndex = 4, chapterCount = 10),
                book("new-a", "New A", chapterIndex = 70, chapterCount = 72),
                book("done-a", "Done A", chapterIndex = 9, chapterCount = 10),
                book("fresh", "Fresh", chapterIndex = -1, chapterCount = 10),
            ),
            LibrarySortMode.Updates,
        )
        assertEquals(
            listOf("mid-a", "new-a", "new-b", "fresh", "done-a", "done-b"),
            sorted.map { it.bookId },
        )
    }

    @Test
    fun updatesBand() {
        assertEquals(0, LibrarySort.updatesBand(72, 70)) // 1+ unread ahead
        assertEquals(0, LibrarySort.updatesBand(10, 4)) // still unread ahead
        assertEquals(1, LibrarySort.updatesBand(10, -1)) // unstarted
        assertEquals(2, LibrarySort.updatesBand(10, 9)) // complete
    }

    @Test
    fun parseFallsBack() {
        assertEquals(
            LibrarySortMode.Updates,
            LibrarySort.parse("nope", LibrarySort.pluginModes, LibrarySortMode.Updates),
        )
        assertEquals(
            LibrarySortMode.Title,
            LibrarySort.parse("Title", LibrarySort.filesModes, LibrarySortMode.DateAdded),
        )
        assertEquals(
            LibrarySortMode.DateAdded,
            LibrarySort.parse("Updates", LibrarySort.filesModes, LibrarySortMode.DateAdded),
        )
    }
}
