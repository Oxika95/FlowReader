package com.personal.flowreader.plugin.updates

import com.personal.flowreader.plugin.api.PluginChapterRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDiffTest {
    private fun toc(vararg urls: String) = urls.map { PluginChapterRef(title = "T $it", url = it) }

    @Test
    fun appendedChaptersAreNew() {
        val new = UpdateDiff.newChapters(listOf("a", "b"), toc("a", "b", "c", "d"))
        assertEquals(listOf("c", "d"), new.map { it.url })
    }

    @Test
    fun retitledChaptersAreNotNew() {
        val new = UpdateDiff.newChapters(
            listOf("a", "b"),
            listOf(PluginChapterRef("Renamed", "a"), PluginChapterRef("Also renamed", "b")),
        )
        assertTrue(new.isEmpty())
    }

    @Test
    fun reorderedOrShrunkTocHasNoNewChapters() {
        assertTrue(UpdateDiff.newChapters(listOf("a", "b", "c"), toc("c", "a", "b")).isEmpty())
        assertTrue(UpdateDiff.newChapters(listOf("a", "b", "c"), toc("a", "c")).isEmpty())
    }

    @Test
    fun insertedChapterCounts() {
        assertEquals(listOf("x"), UpdateDiff.newChapters(listOf("a", "b"), toc("a", "x", "b")).map { it.url })
    }

    @Test
    fun urlSchemeChangeOnlyCountsGrowth() {
        assertEquals(listOf("n3"), UpdateDiff.newChapters(listOf("a", "b"), toc("n1", "n2", "n3")).map { it.url })
        assertTrue(UpdateDiff.newChapters(listOf("a", "b"), toc("n1", "n2")).isEmpty())
    }

    @Test
    fun noBaselineMeansNothingNew() {
        assertTrue(UpdateDiff.newChapters(emptyList(), toc("a", "b")).isEmpty())
    }

    @Test
    fun notifyDefaultsToSyncableListMembership() {
        val syncable = setOf("follow")
        assertTrue(UpdateDiff.notifyOn(null, setOf("follow", "favorite"), syncable))
        assertFalse(UpdateDiff.notifyOn(null, setOf("favorite"), syncable))
        assertFalse(UpdateDiff.notifyOn(false, setOf("follow"), syncable))
        assertTrue(UpdateDiff.notifyOn(true, emptySet(), syncable))
    }

    @Test
    fun headlineCountsChapters() {
        assertEquals("New chapter", UpdateDiff.headline(1))
        assertEquals("3 new chapters", UpdateDiff.headline(3))
    }
}
