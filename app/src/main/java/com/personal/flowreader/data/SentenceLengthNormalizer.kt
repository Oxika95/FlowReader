package com.personal.flowreader.data

/**
 * Post-process heuristic sentence parts into a char band suited to Edge TTS latency:
 * join crumbs forward, split oversize clips at commas (nearest to [targetChars]).
 *
 * Defaults from live Edge bench (EdgeSynthLengthBench): ~0.5s fixed request tax favors
 * longer clips; outliers appear around 400+ chars.
 */
internal object SentenceLengthNormalizer {
    const val DEFAULT_TARGET_CHARS = 75
    const val DEFAULT_FLEX_CHARS = 25
    const val MIN_TARGET_CHARS = 75
    const val MAX_TARGET_CHARS = 300
    const val MIN_FLEX_CHARS = 0
    const val MAX_FLEX_CHARS = 50
    /** Absolute floor/ceiling for derived min/max (before hard Edge cap). */
    private const val BAND_FLOOR = 20
    private const val BAND_CEILING = 500

    private val softBreaks = listOf(", ", "; ", ": ", " — ", " – ", " - ")

    data class Band(val target: Int, val min: Int, val max: Int)

    fun band(
        targetChars: Int = DEFAULT_TARGET_CHARS,
        flexChars: Int = DEFAULT_FLEX_CHARS,
    ): Band {
        val target = targetChars.coerceIn(MIN_TARGET_CHARS, MAX_TARGET_CHARS)
        val flex = flexChars.coerceIn(MIN_FLEX_CHARS, MAX_FLEX_CHARS)
        val min = (target - flex).coerceAtLeast(BAND_FLOOR)
        val max = (target + flex).coerceAtMost(BAND_CEILING).coerceAtLeast(min)
        return Band(target, min, max)
    }

    fun normalize(
        parts: List<String>,
        targetChars: Int = DEFAULT_TARGET_CHARS,
        flexChars: Int = DEFAULT_FLEX_CHARS,
    ): List<String> {
        if (parts.isEmpty()) return emptyList()
        val b = band(targetChars, flexChars)
        val joined = joinShorts(parts, b)
        return splitLongs(joined, b)
    }

    private fun joinShorts(parts: List<String>, b: Band): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < parts.size) {
            var cur = parts[i]
            while (cur.length < b.min && i + 1 < parts.size) {
                val next = parts[i + 1]
                if (cur.length + 1 + next.length > b.max) break
                cur = "$cur $next"
                i++
            }
            out += cur
            i++
        }
        return out
    }

    private fun splitLongs(parts: List<String>, b: Band): List<String> {
        val out = ArrayList<String>()
        for (part in parts) {
            splitOne(part, out, b)
        }
        return out
    }

    private fun splitOne(text: String, out: MutableList<String>, b: Band) {
        if (text.length <= b.max) {
            out += text
            return
        }
        val breakAt = findBreak(text, b)
        if (breakAt == null || breakAt <= 0 || breakAt >= text.length) {
            val hard = hardBreak(text, b)
            val left = text.substring(0, hard).trimEnd()
            val right = text.substring(hard).trimStart()
            if (left.isNotEmpty()) splitOne(left, out, b)
            if (right.isNotEmpty()) splitOne(right, out, b)
            return
        }
        val left = text.substring(0, breakAt).trimEnd()
        val right = text.substring(breakAt).trimStart()
        if (left.isNotEmpty()) splitOne(left, out, b)
        if (right.isNotEmpty()) splitOne(right, out, b)
    }

    private fun findBreak(text: String, b: Band): Int? {
        val windowLo = b.min.coerceAtMost(text.length)
        val windowHi = (text.length - b.min).coerceAtLeast(windowLo)
        if (windowLo >= windowHi) return null

        var bestPos: Int? = null
        var bestDist = Int.MAX_VALUE
        for (marker in softBreaks) {
            var from = 0
            while (from < text.length) {
                val idx = text.indexOf(marker, from)
                if (idx < 0) break
                val after = idx + marker.length
                if (after in windowLo..windowHi) {
                    val dist = kotlin.math.abs(after - b.target)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestPos = after
                    }
                }
                from = idx + 1
            }
        }
        return bestPos
    }

    private fun hardBreak(text: String, b: Band): Int {
        val ideal = b.target.coerceIn(1, text.length - 1)
        val windowLo = b.min.coerceAtMost(ideal)
        val windowHi = (text.length - b.min).coerceAtLeast(ideal)
        val before = text.lastIndexOf(' ', ideal).takeIf { it >= windowLo }
        if (before != null && before > 0) return before + 1
        val after = text.indexOf(' ', ideal).takeIf { it in ideal until windowHi }
        if (after != null) return after + 1
        return ideal
    }
}
