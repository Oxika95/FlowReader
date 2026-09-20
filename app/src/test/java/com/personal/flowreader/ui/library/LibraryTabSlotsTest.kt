package com.personal.flowreader.ui.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTabSlotsTest {
    @Test
    fun shortNamesGrowToEqualSlots() {
        val layout = LibraryTabSlots.layout(
            availablePx = 360,
            insetPx = 16,
            gapPx = 16,
            minWidthsPx = intArrayOf(40, 48, 32),
        )
        assertFalse(layout.overflow)
        assertArrayEquals(intArrayOf(99, 99, 98), layout.slotWidthsPx)
        assertEquals(296, layout.slotWidthsPx.sum())
    }

    @Test
    fun longNameKeepsFullWidthAndOthersGrow() {
        val layout = LibraryTabSlots.layout(
            availablePx = 360,
            insetPx = 16,
            gapPx = 16,
            minWidthsPx = intArrayOf(40, 48, 140, 32),
        )
        assertFalse(layout.overflow)
        assertTrue(layout.slotWidthsPx[2] >= 140)
        assertTrue(layout.slotWidthsPx[0] >= 40)
        assertTrue(layout.slotWidthsPx[1] >= 48)
        assertTrue(layout.slotWidthsPx[3] >= 32)
        assertEquals(280, layout.slotWidthsPx.sum())
    }

    @Test
    fun overflowWhenMinsCannotFit() {
        val layout = LibraryTabSlots.layout(
            availablePx = 160,
            insetPx = 16,
            gapPx = 16,
            minWidthsPx = intArrayOf(48, 48, 48, 48),
        )
        assertTrue(layout.overflow)
        assertArrayEquals(intArrayOf(48, 48, 48, 48), layout.slotWidthsPx)
    }
}
