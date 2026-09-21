package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {
    @Test
    fun defaultIsSlightlyLargerThanOne() {
        assertEquals(1.10f, UiScale.DEFAULT, 0.0001f)
    }

    @Test
    fun coerceLeavesInRangeValuesUnchanged() {
        assertEquals(0.75f, UiScale.coerce(0.75f), 0.0001f)
        assertEquals(1.10f, UiScale.coerce(1.10f), 0.0001f)
        assertEquals(1.25f, UiScale.coerce(1.25f), 0.0001f)
        assertEquals(1.00f, UiScale.coerce(1.00f), 0.0001f)
    }

    @Test
    fun coerceClampsBelowMin() {
        assertEquals(UiScale.MIN, UiScale.coerce(0.5f), 0.0001f)
        assertEquals(UiScale.MIN, UiScale.coerce(-1f), 0.0001f)
    }

    @Test
    fun coerceClampsAboveMax() {
        assertEquals(UiScale.MAX, UiScale.coerce(1.35f), 0.0001f)
        assertEquals(UiScale.MAX, UiScale.coerce(10f), 0.0001f)
    }

    @Test
    fun missingPrefResolvesToDefault() {
        val stored: Float? = null
        assertEquals(UiScale.DEFAULT, UiScale.coerce(stored ?: UiScale.DEFAULT), 0.0001f)
    }

    @Test
    fun readerPrefsDefaultIncludesUiScale() {
        val prefs = ReaderPrefs()
        assertEquals(UiScale.DEFAULT, prefs.uiScale, 0.0001f)
    }

    @Test
    fun readerPrefsRoundTripKeepsUiScale() {
        val original = ReaderPrefs(uiScale = 1.25f)
        val copy = original.copy()
        assertEquals(1.25f, copy.uiScale, 0.0001f)
        assertEquals(original, copy)
    }

    @Test
    fun stepAndRangeAreConsistent() {
        val steps = ((UiScale.MAX - UiScale.MIN) / UiScale.STEP).toInt()
        assertEquals(10, steps)
    }
}
