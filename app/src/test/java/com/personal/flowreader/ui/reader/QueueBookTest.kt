package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.BookMeter
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.PreparedChapter
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.data.ReadingWindow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QueueBookTest {
    private fun book(title: String, chapters: Int, prefix: String): ReaderBook {
        val titles = List(chapters) { "Part ${it + 1}" }
        return ReaderBook(
            title = title,
            chapterCount = chapters,
            titles = titles,
            toc = titles.mapIndexed { i, t -> i to t },
            meter = BookMeter(LongArray(chapters) { 1L }),
            hrefs = { "$prefix$it" },
            hrefIndex = { it.removePrefix(prefix).toIntOrNull() ?: -1 },
            legacyLocus = { it },
            load = { i ->
                Chapter(
                    titles[i],
                    listOf(
                        Block("t$i-0", BlockKind.Paragraph, "$title chapter $i first."),
                        Block("t$i-1", BlockKind.Paragraph, "$title chapter $i second."),
                    ),
                )
            },
        )
    }

    private fun segment(id: String, chapters: Int, local: List<FilterRule> = emptyList()) =
        QueueSegment(id, "book-$id", "Item $id", done = false, storedPath = "", book = book("Item $id", chapters, "h"), local = local)

    private val queue = QueueBook(listOf(segment("a", 1), segment("b", 3), segment("c", 1)))

    @Test
    fun chaptersConcatenateInQueueOrder() {
        assertEquals(5, queue.chapterCount)
        assertEquals(listOf(0, 1, 1, 1, 2), (0 until 5).map(queue::segmentIndexOf))
        assertEquals(Locus(3, 1, 4), queue.toGlobal(1, Locus(2, 1, 4)))
        assertEquals(1 to Locus(2, 1, 4), queue.toLocal(Locus(3, 1, 4)))
        assertEquals(4, queue.baseOf(2))
    }

    @Test
    fun tocNestsChaptersUnderMultiChapterItems() {
        assertEquals(listOf("Item a", "Item b", "Part 1", "Part 2", "Part 3", "Item c"), queue.reader.toc.map { it.second })
        assertEquals(listOf(0, 1, 1, 2, 3, 4), queue.reader.toc.map { it.first })
        assertEquals(listOf(0, 0, 1, 1, 1, 0), queue.tocLevels)
        assertEquals(4, queue.reader.tocRowOf(3))
    }

    @Test
    fun loadPrefixesBlockIdsAndTitlesEachItemStart() = runBlocking {
        val first = queue.reader.load(0)
        val inB = queue.reader.load(2)
        assertEquals("Item a", first.title)
        assertEquals("q0-t0-0", first.blocks[0].id)
        assertEquals("Part 2", inB.title)
        assertEquals("q1-t1-0", inB.blocks[0].id)
        assertEquals("Item b", queue.reader.load(1).title)
        assertEquals("Item c", queue.reader.chapterTitles[4])
    }

    @Test
    fun hrefsRoundTripThroughTheItem() {
        val href = queue.reader.href(3)
        assertEquals("b#h2", href)
        assertEquals(3, queue.reader.indexOfHref(href))
        assertEquals(-1, queue.reader.indexOfHref("missing#h0"))
    }

    @Test
    fun locatorWritesTheItemsOwnPosition() = runBlocking {
        val prepared = PreparedChapter.of(3, queue.reader.load(3), emptyList(), 75, 25)
        val session = ReadingSession(
            bookId = QueueBook.ID,
            contentKey = 0,
            initial = ReadingWindow.of(QueueBook.TITLE, queue.reader.chapterTitles, listOf(prepared), origin = 3),
            chapterCount = { queue.chapterCount },
            loader = { _, _, _ -> null },
        )
        val update = queue.locator(session).locate(Locus(3, 1, 0), at = 42L)
        assertEquals("book-b", update.bookId)
        assertEquals(2, update.chapterIndex)
        assertEquals(1, update.blockIndex)
        assertEquals("h2", update.chapterHref)
        assertEquals((2 + 0.5f) / 3f, update.fraction!!, 1e-4f)
    }

    @Test
    fun contentKeyTracksOrderAndLocalRules() {
        val global = emptyList<FilterRule>()
        val reordered = QueueBook(listOf(segment("b", 3), segment("a", 1), segment("c", 1)))
        val filtered = queue.withLocal(1, listOf(FilterRule(id = "r", pattern = "x", replacement = "y")))
        val base = queue.contentKey(global, global)
        assertNotEquals(base, reordered.contentKey(global, global))
        assertNotEquals(base, filtered.contentKey(global, global))
        assertEquals(base, QueueBook(queue.segments).contentKey(global, global))
    }
}
