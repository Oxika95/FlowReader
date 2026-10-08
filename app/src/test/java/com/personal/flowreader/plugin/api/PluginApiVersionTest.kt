package com.personal.flowreader.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiVersionTest {
    @Test
    fun hostAcceptsOnlyTheCurrentVersion() {
        assertEquals(3, PLUGIN_HOST_API_VERSION)
        assertEquals(PLUGIN_HOST_API_VERSION, PLUGIN_MIN_API_VERSION)
        assertTrue(2 !in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION)
        assertTrue(4 !in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION)
    }

    @Test
    fun manifest_readsUpdatesCapability() {
        val m = PluginManifest.parse("""{"id":"demo","apiVersion":3,"capabilities":["updates","search"]}""")
        assertTrue(m.has(PluginCapability.Updates))
    }

    @Test
    fun updateInfo_newerWhenCountGrowsOrLatestMoves() {
        val q = PluginUpdateQuery(id = "1", url = "u", chapters = 10, lastChapterUrl = "c10")
        assertTrue(PluginUpdateInfo("1", chapters = 11).isNewer(q))
        assertTrue(PluginUpdateInfo("1", latestUrl = "c11").isNewer(q))
        assertTrue(!PluginUpdateInfo("1", chapters = 10).isNewer(q))
        assertTrue(!PluginUpdateInfo("1", chapters = 9, latestUrl = "c10").isNewer(q))
        assertTrue(!PluginUpdateInfo("1").isNewer(q))
    }

    @Test
    fun manifest_defaultsToV1() {
        val m = PluginManifest.parse("""{"id":"demo","name":"Demo"}""")
        assertEquals(1, m.apiVersion)
    }

    @Test
    fun manifest_readsDeclaredVersion() {
        val m = PluginManifest.parse("""{"id":"demo","apiVersion":3}""")
        assertEquals(3, m.apiVersion)
    }
}
