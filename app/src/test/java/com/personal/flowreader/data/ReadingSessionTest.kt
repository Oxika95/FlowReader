package com.personal.flowreader.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionTest {
    private val count = 10
    private val loads = ArrayList<Int>()

    private fun raw(i: Int) = Chapter("Ch $i", List(2) { b -> Block("c$i-$b", BlockKind.Paragraph, "Chapter $i block $b.") })

    private fun prepared(i: Int) = PreparedChapter.of(i, raw(i), emptyList(), targetChars = 200, flexChars = 50)

    private fun session(center: Int) = ReadingSession(
        bookId = "b",
        contentKey = 0,
        initial = ReadingWindow.of("Book", List(count) { "Ch $it" }, listOf(prepared(center)), origin = center),
        chapterCount = { count },
        loader = { i, _, _ -> loads += i; prepared(i) },
    )

    @Test
    fun loadsOnlyAdjacentChapters() = runBlocking {
        val s = session(4)
        assertTrue(s.loadAdjacent(5, 200, 50))
        assertTrue(s.loadAdjacent(3, 200, 50))
        assertFalse(s.loadAdjacent(8, 200, 50))
        assertEquals(3..5, s.window.value.loaded)
        assertEquals(listOf(5, 3), loads)
    }

    @Test
    fun boundsRespected() = runBlocking {
        val s = session(0)
        assertFalse(s.loadAdjacent(-1, 200, 50))
        val last = session(count - 1)
        assertFalse(last.loadNext(200, 50))
    }

    @Test
    fun trimsAroundEveryFocus() = runBlocking {
        val s = session(2)
        s.setFocus(ReadingSession.FOCUS_TTS, 2)
        repeat(4) { s.loadNext(200, 50) }
        // A requested chapter survives its own load; the next focus change trims to 1..3.
        assertEquals(2..6, s.window.value.loaded)
        s.setFocus(ReadingSession.FOCUS_TTS, 2)
        assertEquals(2..3, s.window.value.loaded)
        s.setFocus(ReadingSession.FOCUS_READER, 6)
        repeat(4) { s.loadNext(200, 50) }
        assertEquals(2..7, s.window.value.loaded)
        s.setFocus(ReadingSession.FOCUS_TTS, null)
        assertEquals(5..7, s.window.value.loaded)
    }

    @Test
    fun windowDocKeepsAllChapterSlots() = runBlocking {
        val s = session(4)
        s.loadAdjacent(5, 200, 50)
        val doc = s.window.value.doc
        assertEquals(count, doc.chapters.size)
        assertTrue(doc.chapters[0].blocks.isEmpty())
        assertEquals(2, doc.chapters[5].blocks.size)
        assertEquals("Ch 0", s.window.value.chapterTitle(0))
        val items = doc.readingItems(includeChapterTitles = true)
        assertEquals(setOf(4, 5), items.map { it.chapterIndex }.toSet())
    }

    @Test
    fun resplitRestartsNumberingAtFirstLoadedChapter() = runBlocking {
        val s = session(4)
        s.loadAdjacent(3, 200, 50)
        s.resplit(80, 20)
        assertEquals(3, s.window.value.origin)
        assertEquals(SentenceTable.ORIGIN, s.window.value.table.firstIndex)
    }
}
