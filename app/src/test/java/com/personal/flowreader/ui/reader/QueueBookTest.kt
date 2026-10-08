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

    private fun same(s: QueueSegment, done: Boolean = s.done) =
        QueueSegment(s.queId, s.bookId, s.title, done, s.storedPath, s.book, s.local)

    @Test
    fun classifyAppendDoneReorderDeleteInsert() {
        val (a, b, c) = queue.segments
        assertEquals(QueueChange.Same, QueueChange.classify(queue, QueueBook(queue.segments.map { same(it) })))
        assertEquals(QueueChange.DoneOnly, QueueChange.classify(queue, QueueBook(listOf(same(a, done = true), b, c))))
        assertEquals(QueueChange.Appended, QueueChange.classify(queue, QueueBook(listOf(a, b, c, segment("d", 2)))))
        assertEquals(QueueChange.Edited, QueueChange.classify(queue, QueueBook(listOf(b, a, c))))
        assertEquals(QueueChange.Edited, QueueChange.classify(queue, QueueBook(listOf(a, c))))
        assertEquals(QueueChange.Edited, QueueChange.classify(queue, QueueBook(listOf(a, segment("d", 1), b, c))))
        assertEquals(QueueChange.Edited, QueueChange.classify(queue, QueueBook(listOf(a, segment("b", 2), c))))
    }

    @Test
    fun appendKeepsExistingChapterIndices() {
        val appended = QueueBook(queue.segments + segment("d", 2))
        assertEquals((0 until 3).map(queue::baseOf), (0 until 3).map(appended::baseOf))
        assertEquals(5, appended.baseOf(3))
        assertEquals(7, appended.chapterCount)
    }

    @Test
    fun remapFollowsMovedRow() {
        val (a, b, c) = queue.segments
        val moved = QueueBook(listOf(c, b, a))
        assertEquals(Locus(3, 1, 2), moved.remap(Locus(3, 1, 2), queue))
        assertEquals(Locus(4, 0, 5), moved.remap(Locus(0, 0, 5), queue))
    }

    @Test
    fun remapDeletedRowGoesToNextThenPrevious() {
        val (a, b, c) = queue.segments
        assertEquals(Locus(1, 0, 0), QueueBook(listOf(a, c)).remap(Locus(2, 1, 3), queue))
        assertEquals(Locus(1, 0, 0), QueueBook(listOf(a, b)).remap(Locus(4, 1, 0), queue))
        assertEquals(null, QueueBook(emptyList()).remap(Locus(4, 1, 0), queue))
    }

    @Test
    fun tocItemsGroupChaptersUnderTheirItem() {
        val items = queue.tocItems
        assertEquals(listOf("a", "b", "c"), items.map { it.queId })
        assertEquals(listOf(0, 1, 5), items.map { it.row })
        assertEquals(listOf(2, 3, 4), items[1].chapters.map { it.first })
        assertEquals(emptyList<Pair<Int, String>>(), items[0].chapters)
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
