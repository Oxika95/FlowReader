package com.personal.flowreader.plugin.updates

import com.personal.flowreader.plugin.api.PluginChapterRef
import com.personal.flowreader.plugin.api.PluginList
import com.personal.flowreader.plugin.api.PluginListKind
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
    fun stubbedChaptersAreNotNewButAppendedOnesAre() {
        assertEquals(listOf("e"), UpdateDiff.newChapters(listOf("a", "b", "c", "d"), toc("c", "d", "e")).map { it.url })
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
    fun notifyListsAreOnlyNotifyDefault() {
        val lists = listOf(
            PluginList("favorites", "Favorites", syncable = true),
            PluginList("follow", "Follow", syncable = true, notifyDefault = true),
            PluginList("later", "Later", syncable = true),
        )
        assertEquals(setOf("follow"), UpdateDiff.notifyLists(lists))
    }

    @Test
    fun storedBellWinsOverListDefault() {
        val notify = setOf("follow")
        assertEquals(true, UpdateDiff.notifyOn(null, setOf("follow"), notify))
        assertEquals(false, UpdateDiff.notifyOn(null, setOf("later"), notify))
        assertEquals(true, UpdateDiff.notifyOn(true, setOf("later"), notify))
        assertEquals(false, UpdateDiff.notifyOn(false, setOf("follow"), notify))
    }

    @Test
    fun listedOrBellOnStoriesAreMonitored() {
        assertEquals(true, UpdateDiff.monitored(notify = false, listedIn = setOf("later")))
        assertEquals(true, UpdateDiff.monitored(notify = true, listedIn = emptySet()))
        assertEquals(false, UpdateDiff.monitored(notify = false, listedIn = emptySet()))
    }

    @Test
    fun browseListsNeverNotify() {
        val lists = listOf(
            PluginList("memberships", "Memberships", syncable = true, kind = PluginListKind.Browse),
            PluginList("follow", "Follow", notifyDefault = true),
        )
        assertEquals(setOf("follow"), UpdateDiff.notifyLists(lists))
    }

    @Test
    fun headlineCountsChapters() {
        assertEquals("New chapter", UpdateDiff.headline(1))
        assertEquals("3 new chapters", UpdateDiff.headline(3))
    }
}
