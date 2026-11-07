package com.personal.flowreader.ui.debug

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class DebugRouteTest {
    @Test
    fun parsesAllowlistedRoutes() {
        assertEquals(DebugRoute.Library, DebugRoute.parse("library"))
        assertEquals(DebugRoute.FirstBook, DebugRoute.parse("reader/first"))
        assertEquals(DebugRoute.Settings(0), DebugRoute.parse("settings/about"))
        assertEquals(DebugRoute.Settings(1), DebugRoute.parse("settings/audio"))
        assertEquals(DebugRoute.Settings(2), DebugRoute.parse("settings/filters"))
        assertEquals(DebugRoute.Settings(3), DebugRoute.parse("settings/import"))
        assertEquals(DebugRoute.Settings(4), DebugRoute.parse("settings/layout"))
        assertEquals(DebugRoute.Settings(5), DebugRoute.parse("settings/plugins"))
        assertEquals(DebugRoute.Plugin("royalroad"), DebugRoute.parse("plugin/royalroad"))
    }

    @Test
    fun rejectsEverythingElse() {
        assertNull(DebugRoute.parse(null))
        assertNull(DebugRoute.parse(""))
        assertNull(DebugRoute.parse("reader/abc"))
        assertNull(DebugRoute.parse("settings/unknown"))
        assertNull(DebugRoute.parse("plugin/"))
        assertNull(DebugRoute.parse("plugin/a/b"))
    }
}
