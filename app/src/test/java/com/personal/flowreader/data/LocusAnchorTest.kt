package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocusAnchorTest {
    private fun chapter(vararg texts: String) =
        Chapter("t", texts.mapIndexed { i, s -> Block("b$i", BlockKind.Paragraph, s) })

    @Test
    fun anchorIsTextAtLocus() {
        val ch = chapter("Alpha beta gamma.")
        assertEquals("beta gamma.", LocusAnchor.of(ch, Locus(0, 0, 6)))
        assertEquals("", LocusAnchor.of(ch, Locus(0, 4, 0)))
    }

    @Test
    fun matchingIndicesKept() {
        val ch = chapter("One two.", "Three four five.")
        val locus = Locus(0, 1, 6)
        assertEquals(locus, LocusAnchor.resolve(ch, locus, LocusAnchor.of(ch, locus)))
    }

    @Test
    fun driftedBlockFoundByText() {
        val before = chapter("Intro.", "The target sentence here.")
        val anchor = LocusAnchor.of(before, Locus(0, 1, 4))
        val after = chapter("New preface.", "Intro.", "Inserted.", "The target sentence here.")
        assertEquals(Locus(0, 3, 4), LocusAnchor.resolve(after, Locus(0, 1, 4), anchor))
    }

    @Test
    fun nearestOccurrenceWins() {
        val ch = chapter("repeat", "x", "y", "z", "repeat")
        assertEquals(Locus(0, 4, 0), LocusAnchor.resolve(ch, Locus(0, 3, 0), "repeat"))
    }

    @Test
    fun missingAnchorClamps() {
        val ch = chapter("Short.")
        assertEquals(Locus(2, 0, 6), LocusAnchor.resolve(ch, Locus(2, 5, 99), "gone"))
        assertEquals(Locus(2, 0, 0), LocusAnchor.resolve(Chapter("t", emptyList()), Locus(2, 5, 99), "x"))
    }
}
