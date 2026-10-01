package com.personal.flowreader.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiVersionTest {
    @Test
    fun hostAcceptsV1AndV2() {
        assertEquals(2, PLUGIN_HOST_API_VERSION)
        assertEquals(1, PLUGIN_MIN_API_VERSION)
        assertTrue(1 in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION)
        assertTrue(2 in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION)
        assertTrue(3 !in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION)
    }

    @Test
    fun manifest_defaultsToV1() {
        val m = PluginManifest.parse("""{"id":"demo","name":"Demo"}""")
        assertEquals(1, m.apiVersion)
    }

    @Test
    fun manifest_readsDeclaredVersion() {
        val m = PluginManifest.parse("""{"id":"demo","apiVersion":2}""")
        assertEquals(2, m.apiVersion)
    }
}
