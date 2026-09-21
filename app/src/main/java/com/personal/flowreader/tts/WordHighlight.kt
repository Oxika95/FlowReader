package com.personal.flowreader.tts

/**
 * Helpers for word-level TTS highlighting driven by Edge word-boundary metadata.
 * Boundary words are matched sequentially against the on-screen sentence text;
 * unmatched words (e.g. TTS-only filter rewrites) are skipped without advancing
 * the search cursor so later words still align.
 */
object WordHighlight {
    /** Edge word-boundary offsets are in 100-nanosecond ticks. */
    const val TICKS_PER_SECOND = 10_000_000L

    data class WordOffset(val start: Int, val end: Int)

    fun computeWordOffsets(text: String, words: List<String>): List<WordOffset?> {
        val offsets = ArrayList<WordOffset?>(words.size)
        var cursor = 0
        for (word in words) {
            val trimmed = word.trim()
            if (trimmed.isEmpty()) {
                offsets.add(null)
                continue
            }
            val index = text.indexOf(trimmed, cursor)
            if (index < 0) {
                offsets.add(null)
                continue
            }
            offsets.add(WordOffset(index, index + trimmed.length))
            cursor = index + trimmed.length
        }
        return offsets
    }

    fun findBoundaryIndexAtTime(
        boundaries: List<EdgeWordBoundary>,
        seconds: Double,
    ): Int {
        if (boundaries.isEmpty()) return -1
        val ticks = (seconds * TICKS_PER_SECOND).toLong()
        var low = 0
        var high = boundaries.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (boundaries[mid].offset <= ticks) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Absolute char range in the block for the word at [mediaTimeSec], or null
     * when boundaries/offsets are unavailable.
     */
    fun highlightRangeInBlock(
        sentence: com.personal.flowreader.data.Sentence,
        boundaries: List<EdgeWordBoundary>,
        mediaTimeSec: Double,
    ): IntRange? {
        if (boundaries.isEmpty()) return null
        val offsets = computeWordOffsets(sentence.text, boundaries.map { it.text })
        val idx = findBoundaryIndexAtTime(boundaries, mediaTimeSec).coerceAtLeast(0)
        val local = offsets.getOrNull(idx) ?: offsets.firstOrNull { it != null } ?: return null
        val start = (sentence.start + local.start).coerceAtLeast(0)
        val end = (sentence.start + local.end).coerceAtLeast(start)
        if (start >= end) return null
        return start until end
    }
}
