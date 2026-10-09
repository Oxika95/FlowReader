package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMigrationTest {
    private fun progress(
        bookId: String,
        sourceKind: String = BookSource.Imported.name,
        inLibrary: Boolean = true,
        chapter: Int = 0,
        updatedAt: Long = 10L,
        shelf: String = "",
    ) = LegacyProgressRow(
        bookId = bookId,
        title = "T $bookId",
        storedPath = "/books/$bookId/book.txt",
        sourceUri = "content://$bookId",
        sourceKind = sourceKind,
        chapterIndex = chapter,
        blockIndex = 2,
        charOffset = 3,
        updatedAt = updatedAt,
        inLibrary = inLibrary,
        readingProgress = 0.75f,
        libraryTabId = shelf,
        anchorText = "anchor",
        chapterHref = "c$chapter",
    )

    @Test
    fun libraryBookKeepsItsPositionAndShelf() {
        val rows = LegacyMigration.map(listOf(progress("a", chapter = 4, shelf = "s1")), emptyList(), mapOf("a" to "[f]"))
        val book = rows.library.single()
        assertEquals("a", book.bookId)
        assertEquals("s1", book.shelfId)
        assertEquals("[f]", book.localFilters)
        assertEquals(4, book.position.chapterIndex)
        assertEquals(2, book.position.blockIndex)
        assertEquals(3, book.position.charOffset)
        assertEquals("c4", book.position.chapterHref)
        assertEquals(0.75f, book.position.fraction, 0f)
        assertEquals(10L, book.position.positionAt)
        assertEquals(PositionSource.Migration.name, book.position.positionSource)
    }

    @Test
    fun bookInLibraryAndQueueGetsTwoIndependentRows() {
        val rows = LegacyMigration.map(
            listOf(progress("a", chapter = 4)),
            listOf(LegacyQueRow(id = "q1", bookId = "a", sortOrder = 0, addedAt = 5L, done = false)),
            emptyMap(),
        )
        assertEquals(1, rows.library.size)
        val item = rows.queue.single()
        assertEquals("q1", item.queId)
        assertEquals("a", item.bookId)
        assertEquals(rows.library.single().storedPath, item.storedPath)
        assertEquals(4, item.position.chapterIndex)
        assertEquals(QueueOrigin.Text.name, item.origin)
    }

    @Test
    fun queueOnlyBookIsNotAddedToLibrary() {
        val rows = LegacyMigration.map(
            listOf(progress("a", sourceKind = BookSource.Linked.name, inLibrary = false)),
            listOf(LegacyQueRow(id = "q1", bookId = "a", sortOrder = 3, addedAt = 5L, done = true)),
            emptyMap(),
        )
        assertTrue(rows.library.isEmpty())
        val item = rows.queue.single()
        assertEquals(QueueOrigin.File.name, item.origin)
        assertEquals(3, item.sortOrder)
        assertTrue(item.done)
    }

    @Test
    fun orphanQueueRowsDrop() {
        val rows = LegacyMigration.map(
            emptyList(),
            listOf(LegacyQueRow(id = "q1", bookId = "missing", sortOrder = 0, addedAt = 5L, done = false)),
            emptyMap(),
        )
        assertTrue(rows.queue.isEmpty())
    }

    @Test
    fun pluginRowsAreStagedNotInLibrary() {
        val rows = LegacyMigration.map(
            listOf(progress("rr:1", sourceKind = "royalroad", chapter = 12)),
            listOf(LegacyQueRow(id = "q1", bookId = "rr:1", sortOrder = 0, addedAt = 5L, done = false)),
            mapOf("rr:1" to "[p]"),
        )
        assertTrue(rows.library.isEmpty())
        val staged = rows.plugin.single()
        assertEquals("rr:1", staged.row.bookId)
        assertEquals(12, staged.row.chapterIndex)
        assertEquals("[p]", staged.localFilters)
        assertEquals(QueueOrigin.Plugin.name, rows.queue.single().origin)
    }
}
