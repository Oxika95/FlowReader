package com.personal.flowreader.ui.plugin

import com.personal.flowreader.plugin.api.PluginList
import com.personal.flowreader.plugin.api.PluginManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncTargetsTest {
    private fun manifest(vararg lists: PluginList) =
        PluginManifest(id = "p", name = "P", version = "1", apiVersion = 4, bookIdPrefix = "p", lists = lists.toList())

    private val follow = PluginList("follow", "Follow", syncable = true)
    private val favorite = PluginList("favorite", "Favorite", syncable = true)
    private val readLater = PluginList("readlater", "Read Later", syncable = true)
    private val local = PluginList("local", "Local")

    @Test
    fun severalSyncableListsOfferSyncAll() {
        val m = manifest(follow, favorite, readLater, local)
        assertEquals(SYNC_ALL_LISTS, defaultSyncChoice(m))
        assertEquals(listOf(follow, favorite, readLater), syncTargets(m, SYNC_ALL_LISTS))
        assertEquals("Follow, Favorite and Read Later", syncTitle(syncTargets(m, SYNC_ALL_LISTS)))
    }

    @Test
    fun oneSyncableListIsOfferedByItself() {
        val m = manifest(follow, local)
        assertEquals("follow", defaultSyncChoice(m))
        assertEquals(listOf(follow), syncTargets(m, "follow"))
        assertEquals("Follow", syncTitle(syncTargets(m, "follow")))
    }

    @Test
    fun noSyncableListsOfferNothing() {
        val m = manifest(local)
        assertNull(defaultSyncChoice(m))
        assertEquals(emptyList<PluginList>(), syncTargets(m, SYNC_ALL_LISTS))
        assertEquals(emptyList<PluginList>(), syncTargets(m, "missing"))
    }

    @Test
    fun twoTitlesJoinWithAnd() {
        assertEquals("Follow and Favorite", syncTitle(listOf(follow, favorite)))
    }
}
