package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TonalUnderlayTest {
    @Test
    fun modeKeysRoundTrip() {
        UnderlayMode.entries.forEach { assertEquals(it, UnderlayMode.fromKey(it.key)) }
        assertNull(UnderlayMode.fromKey(null))
        assertNull(UnderlayMode.fromKey("bogus"))
    }

    @Test
    fun legacyZeroOrMissingLevelIsOff() {
        assertEquals(UnderlayMode.Off, UnderlayMode.legacy(null, ""))
        assertEquals(UnderlayMode.Off, UnderlayMode.legacy(0f, ""))
        assertEquals(UnderlayMode.Off, UnderlayMode.legacy(0f, "AA:BB"))
    }

    @Test
    fun legacyArmedLevelMapsByDevice() {
        assertEquals(UnderlayMode.Always, UnderlayMode.legacy(-60f, ""))
        assertEquals(UnderlayMode.Always, UnderlayMode.legacy(-60f, "  "))
        assertEquals(UnderlayMode.Device, UnderlayMode.legacy(-60f, "AA:BB"))
    }

    @Test
    fun storedLevelReplacesLegacyOffWithDefault() {
        assertEquals(TonalUnderlay.DEFAULT_LEVEL, TonalUnderlay.storedLevel(null), 0f)
        assertEquals(TonalUnderlay.DEFAULT_LEVEL, TonalUnderlay.storedLevel(0f), 0f)
        assertEquals(-80f, TonalUnderlay.storedLevel(-80f), 0f)
        assertEquals(-80f, TonalUnderlay.storedLevel(-82f), 0f)
    }

    @Test
    fun coerceLevelNeverReturnsSilence() {
        assertEquals(-20f, TonalUnderlay.coerceLevel(0f), 0f)
        assertEquals(-20f, TonalUnderlay.coerceLevel(5f), 0f)
        assertEquals(-100f, TonalUnderlay.coerceLevel(-200f), 0f)
        assertEquals(TonalUnderlay.LEVEL_STEPS.lastIndex, TonalUnderlay.levelIndex(-20f))
        assertEquals(0, TonalUnderlay.levelIndex(-100f))
    }

    @Test
    fun offIsSilentRegardlessOfDevice() {
        assertEquals(0f, TonalUnderlay.effectiveLevel(UnderlayMode.Off, -60f, "AA:BB", true), 0f)
    }

    @Test
    fun alwaysIgnoresDevice() {
        assertEquals(-60f, TonalUnderlay.effectiveLevel(UnderlayMode.Always, -60f, "", false), 0f)
        assertEquals(-60f, TonalUnderlay.effectiveLevel(UnderlayMode.Always, -60f, "AA:BB", false), 0f)
    }

    @Test
    fun deviceSoundsOnlyWhenPickedAndConnected() {
        assertEquals(-50f, TonalUnderlay.effectiveLevel(UnderlayMode.Device, -50f, "AA:BB", true), 0f)
        assertEquals(0f, TonalUnderlay.effectiveLevel(UnderlayMode.Device, -50f, "AA:BB", false), 0f)
        assertEquals(0f, TonalUnderlay.effectiveLevel(UnderlayMode.Device, -50f, "", true), 0f)
    }
}
