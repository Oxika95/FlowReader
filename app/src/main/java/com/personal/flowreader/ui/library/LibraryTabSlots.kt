package com.personal.flowreader.ui.library

/**
 * Tab slots: never narrower than the full label ([minWidthsPx]). Extra space
 * goes into the buttons, not into empty gaps. Scroll when the mins cannot fit.
 *
 * [minWidthsPx] is Files, Queue, plugin tabs, then +.
 */
data class LibraryTabLayout(
    val slotWidthsPx: IntArray,
    val overflow: Boolean,
)

object LibraryTabSlots {
    fun layout(
        availablePx: Int,
        insetPx: Int,
        gapPx: Int,
        minWidthsPx: IntArray,
    ): LibraryTabLayout {
        val n = minWidthsPx.size
        if (n == 0) return LibraryTabLayout(IntArray(0), false)
        val inner = (availablePx - insetPx * 2).coerceAtLeast(0)
        val gaps = gapPx * (n - 1).coerceAtLeast(0)
        val minTotal = minWidthsPx.sum() + gaps
        if (minTotal > inner) {
            return LibraryTabLayout(minWidthsPx.copyOf(), overflow = true)
        }
        val forSlots = (inner - gaps).coerceAtLeast(0)
        val maxSlot = forSlots / n
        val widths = minWidthsPx.copyOf()
        var leftover = inner - minTotal
        leftover = growToward(widths, cap = maxSlot, leftover)
        growToward(widths, cap = Int.MAX_VALUE, leftover)
        return LibraryTabLayout(widths, overflow = false)
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
