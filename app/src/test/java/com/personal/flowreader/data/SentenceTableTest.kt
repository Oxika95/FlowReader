package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceTableTest {
    private fun chapter(ci: Int, blocks: Int, perBlock: Int = 2) = List(blocks * perBlock) { i ->
        val bi = i / perBlock
        val k = i % perBlock
        Sentence(ci, bi, k * 10, k * 10 + 10, "c$ci b$bi s$k")
    }

    @Test
    fun originChapterStartsAtOrigin() {
        val t = SentenceTable.of(mapOf(4 to chapter(4, 2), 5 to chapter(5, 1)), origin = 5)
        assertEquals(SentenceTable.ORIGIN - 4, t.firstIndex)
        assertEquals("c5 b0 s0", t.getOrNull(SentenceTable.ORIGIN)!!.text)
        assertEquals(4..5, t.chapters)
    }

    @Test
    fun growingAndTrimmingNeverRenumbers() {
        val t0 = SentenceTable.of(mapOf(3 to chapter(3, 2)), origin = 3)
        val at = t0.indexAt(Locus(3, 1, 12))
        val grown = t0.withChapter(4, chapter(4, 1)).withChapter(2, chapter(2, 3))
        assertEquals(at, grown.indexAt(Locus(3, 1, 12)))
        assertEquals("c3 b1 s1", grown.getOrNull(at)!!.text)
        val trimmed = grown.retain(3..4)
        assertEquals(at, trimmed.indexAt(Locus(3, 1, 12)))
        assertEquals(3..4, trimmed.chapters)
        assertEquals(null, trimmed.getOrNull(SentenceTable.ORIGIN - 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonAdjacentChapterRejected() {
        SentenceTable.of(mapOf(3 to chapter(3, 1)), origin = 3).withChapter(5, chapter(5, 1))
    }

    @Test
    fun inBlockAndFallbacks() {
        val t = SentenceTable.of(mapOf(0 to chapter(0, 3)), origin = 0)
        assertEquals((SentenceTable.ORIGIN + 2)..(SentenceTable.ORIGIN + 3), t.inBlock(0, 1))
        assertTrue(t.inBlock(0, 9).isEmpty())
        // Offset past the block's sentences: block's first sentence.
        assertEquals(SentenceTable.ORIGIN + 2, t.indexAt(Locus(0, 1, 99)))
        // Unloaded later chapter: last sentence; earlier: first.
        assertEquals(t.lastIndex, t.indexAt(Locus(7, 0, 0)))
        val later = SentenceTable.of(mapOf(2 to chapter(2, 1)), origin = 2)
        assertEquals(later.firstIndex, later.indexAt(Locus(0, 5, 0)))
        assertEquals(SentenceTable.ORIGIN, SentenceTable.EMPTY.indexAt(Locus(1, 0, 0)))
    }

    @Test
    fun retainOutsideRangeIsEmpty() {
        val t = SentenceTable.of(mapOf(2 to chapter(2, 1)), origin = 2)
        assertTrue(t.retain(5..6).isEmpty())
    }
}
