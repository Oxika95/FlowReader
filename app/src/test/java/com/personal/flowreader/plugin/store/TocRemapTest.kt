package com.personal.flowreader.plugin.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TocRemapTest {
    private val old = listOf("c1", "c2", "c3", "c4", "c5")

    @Test
    fun frontRemovalKeepsSavedChapterByUrl() {
        val stubbed = listOf("c3", "c4", "c5")
        assertEquals(TocRemap.Moved(1, "c4", sameChapter = true), TocRemap.position("c4", 3, old, stubbed))
    }

    @Test
    fun removedSavedChapterMovesToNextSurvivor() {
        val stubbed = listOf("c1", "c4", "c5")
        assertEquals(TocRemap.Moved(1, "c4", sameChapter = false), TocRemap.position("c2", 1, old, stubbed))
    }

    @Test
    fun removedTailFallsBackToLastSurvivor() {
        val stubbed = listOf("c1", "c2")
        assertEquals(TocRemap.Moved(1, "c2", sameChapter = false), TocRemap.position("c5", 4, old, stubbed))
    }

    @Test
    fun blankHrefUsesOldIndex() {
        val stubbed = listOf("c3", "c4", "c5")
        assertEquals(TocRemap.Moved(0, "c3", sameChapter = true), TocRemap.position("", 2, old, stubbed))
    }

    @Test
    fun unchangedPositionIsNull() {
        assertNull(TocRemap.position("c2", 1, old, old + "c6"))
    }

    @Test
    fun survivorFollowsUrlThenNext() {
        val stubbed = listOf("c4", "c5")
        assertEquals(0, TocRemap.survivor(old, stubbed, 3))
        assertEquals(0, TocRemap.survivor(old, stubbed, 0))
        assertEquals(1, TocRemap.survivor(old, stubbed, 4))
        assertNull(TocRemap.survivor(old, emptyList(), 2))
    }

    @Test
    fun chapterMovesFollowUrls() {
        assertEquals(mapOf(0 to 1, 1 to null, 2 to 2), TocRemap.chapterMoves(listOf("a", "b", "c"), listOf("x", "a", "c", "b2")))
    }

    @Test
    fun chapterMovesPairDuplicateUrlsInOrder() {
        assertEquals(mapOf(0 to 1, 1 to 2), TocRemap.chapterMoves(listOf("a", "a"), listOf("z", "a", "a")))
    }
}
