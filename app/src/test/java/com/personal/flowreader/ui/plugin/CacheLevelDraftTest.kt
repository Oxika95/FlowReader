package com.personal.flowreader.ui.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheLevelDraftTest {
    @Test
    fun keepsOnlyFourDigits() {
        assertEquals("1234", CacheLevelDraft.digits("1a2-3 4567"))
        assertEquals("", CacheLevelDraft.digits("ab"))
    }

    @Test
    fun savesTheWholeTypedNumberNotItsPrefixes() {
        // Typing 15 passes through "1"; only the committed draft is saved.
        val typed = listOf("", "1", "15")
        assertNull(CacheLevelDraft.toSave(typed[1], saved = 1))
        assertEquals(15, CacheLevelDraft.toSave(typed[2], saved = 1))
    }

    @Test
    fun blankOrUnchangedDraftSavesNothing() {
        assertNull(CacheLevelDraft.toSave("", saved = 3))
        assertNull(CacheLevelDraft.toSave("3", saved = 3))
    }

    @Test
    fun zeroIsASavableLevel() {
        assertEquals(0, CacheLevelDraft.toSave("0", saved = 5))
    }
}
