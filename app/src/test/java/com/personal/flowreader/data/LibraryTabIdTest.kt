package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTabIdTest {
    @Test
    fun parsesLegacyEnumNames() {
        assertEquals(LibraryTabId.Files, LibraryTabId.parse("Files", emptySet()))
        assertEquals(LibraryTabId.Que, LibraryTabId.parse("Que", emptySet()))
    }

    @Test
    fun parsesStableIds() {
        assertEquals(LibraryTabId.Files, LibraryTabId.parse("files", emptySet()))
        assertEquals(LibraryTabId.Que, LibraryTabId.parse("que", emptySet()))
    }

    @Test
    fun parsesKnownPlugin() {
        val tab = LibraryTabId.parse("royalroad", setOf("royalroad"))
        assertEquals(LibraryTabId.Plugin("royalroad"), tab)
        assertEquals("royalroad", tab.persistKey)
    }

    @Test
    fun unknownPluginFallsBackToFiles() {
        assertEquals(LibraryTabId.Files, LibraryTabId.parse("missing", setOf("royalroad")))
        assertEquals(LibraryTabId.Files, LibraryTabId.parse(null, emptySet()))
    }
}
