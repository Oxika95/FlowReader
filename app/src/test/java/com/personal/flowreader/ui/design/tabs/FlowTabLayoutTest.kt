package com.personal.flowreader.ui.design.tabs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowTabLayoutTest {
    @Test
    fun emptyTabs_noSlots() {
        val layout = FlowTabLayout.layout(availablePx = 400, insetPx = 16, gapPx = 8, minWidthsPx = IntArray(0))
        assertEquals(0, layout.slotWidthsPx.size)
        assertFalse(layout.overflow)
    }

    @Test
    fun fits_fillsInnerWidthExactly() {
        val layout = FlowTabLayout.layout(availablePx = 400, insetPx = 16, gapPx = 8, minWidthsPx = intArrayOf(60, 80, 40))
        assertFalse(layout.overflow)
        assertEquals(400 - 32 - 16, layout.slotWidthsPx.sum())
        // Never narrower than the label.
        assertTrue(layout.slotWidthsPx[0] >= 60)
        assertTrue(layout.slotWidthsPx[1] >= 80)
        assertTrue(layout.slotWidthsPx[2] >= 40)
    }

    @Test
    fun fits_equalShareWhenLabelsAreShort() {
        val layout = FlowTabLayout.layout(availablePx = 316, insetPx = 0, gapPx = 8, minWidthsPx = intArrayOf(20, 20, 20))
        assertArrayEquals(intArrayOf(100, 100, 100), layout.slotWidthsPx)
    }

    @Test
    fun wideLabel_keepsMinimum_othersShareRemainder() {
        val layout = FlowTabLayout.layout(availablePx = 300, insetPx = 0, gapPx = 0, minWidthsPx = intArrayOf(200, 10, 10))
        assertFalse(layout.overflow)
        assertEquals(200, layout.slotWidthsPx[0])
        assertEquals(50, layout.slotWidthsPx[1])
        assertEquals(50, layout.slotWidthsPx[2])
    }

    @Test
    fun tooWide_overflowsAndKeepsMinimums() {
        val mins = intArrayOf(150, 150, 150)
        val layout = FlowTabLayout.layout(availablePx = 300, insetPx = 16, gapPx = 8, minWidthsPx = mins)
        assertTrue(layout.overflow)
        assertArrayEquals(mins, layout.slotWidthsPx)
    }

    @Test
    fun offsetOf_sumsPreviousSlotsAndGaps() {
        val widths = intArrayOf(100, 50, 70)
        assertEquals(0, FlowTabLayout.offsetOf(0, widths, 8))
        assertEquals(108, FlowTabLayout.offsetOf(1, widths, 8))
        assertEquals(166, FlowTabLayout.offsetOf(2, widths, 8))
        assertEquals(FlowTabLayout.offsetOf(3, widths, 8), FlowTabLayout.offsetOf(99, widths, 8))
    }
}
