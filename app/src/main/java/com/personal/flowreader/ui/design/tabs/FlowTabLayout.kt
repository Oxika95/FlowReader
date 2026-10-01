package com.personal.flowreader.ui.design.tabs

/**
 * Result of [FlowTabLayout.layout]: one width per slot, and whether the bar must scroll.
 */
class FlowTabSlots(
    val slotWidthsPx: IntArray,
    val overflow: Boolean,
)

/**
 * Slot sizing for [FlowTabBar]: a slot is never narrower than its full label ([minWidthsPx]).
 * Leftover width goes into the slots (first up to an equal share, then uncapped), never into
 * empty gaps. When the minimums cannot fit, the bar scrolls horizontally.
 */
object FlowTabLayout {
    fun layout(
        availablePx: Int,
        insetPx: Int,
        gapPx: Int,
        minWidthsPx: IntArray,
    ): FlowTabSlots {
        val n = minWidthsPx.size
        if (n == 0) return FlowTabSlots(IntArray(0), false)
        val inner = (availablePx - insetPx * 2).coerceAtLeast(0)
        val gaps = gapPx * (n - 1).coerceAtLeast(0)
        val minTotal = minWidthsPx.sum() + gaps
        if (minTotal > inner) {
            return FlowTabSlots(minWidthsPx.copyOf(), overflow = true)
        }
        val forSlots = (inner - gaps).coerceAtLeast(0)
        val maxSlot = forSlots / n
        val widths = minWidthsPx.copyOf()
        var leftover = inner - minTotal
        leftover = growToward(widths, cap = maxSlot, leftover)
        growToward(widths, cap = Int.MAX_VALUE, leftover)
        return FlowTabSlots(widths, overflow = false)
    }

    /** Scroll offset that brings slot [index] into view (its start aligned to the bar start). */
    fun offsetOf(index: Int, slotWidthsPx: IntArray, gapPx: Int): Int {
        var x = 0
        for (i in 0 until index.coerceIn(0, slotWidthsPx.size)) x += slotWidthsPx[i] + gapPx
        return x
    }

    /** Add leftover pixels to slots that are still below [cap]. Returns unused leftover. */
    internal fun growToward(widths: IntArray, cap: Int, leftover: Int): Int {
        var left = leftover
        if (left <= 0 || widths.isEmpty()) return left.coerceAtLeast(0)
        while (left > 0) {
            val growable = widths.indices.filter { widths[it] < cap }
            if (growable.isEmpty()) return left
            val each = left / growable.size
            if (each == 0) {
                for (i in 0 until left) {
                    widths[growable[i]]++
                }
                return 0
            }
            val room = growable.minOf { cap - widths[it] }
            val add = minOf(each, room)
            for (i in growable) widths[i] += add
            left -= add * growable.size
        }
        return 0
    }
}
