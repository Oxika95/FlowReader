package com.personal.flowreader.tts

import com.personal.flowreader.data.Sentence

/**
 * Helpers for word-level TTS highlighting driven by timed cues (Edge word boundaries
 * or System [android.speech.tts.UtteranceProgressListener.onRangeStart]).
 *
 * Boundary words are matched sequentially against the on-screen sentence text;
 * unmatched words (e.g. TTS-only filter rewrites) are skipped without advancing
 * the search cursor so later words still align.
 */
object WordHighlight {
    /** Edge word-boundary offsets are in 100-nanosecond ticks. */
    const val TICKS_PER_SECOND = 10_000_000L

    data class WordOffset(val start: Int, val end: Int)

    /** Timed cue mapped into absolute block character indices. */
    data class TimedCue(
        val startSec: Double,
        val endSec: Double?,
        val charRange: IntRange,
    )

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

    /** Build absolute-block cues from Edge boundaries + sentence text. */
    fun cuesFromBoundaries(
        sentence: Sentence,
        boundaries: List<EdgeWordBoundary>,
    ): List<TimedCue> {
        if (boundaries.isEmpty()) return emptyList()
        val offsets = computeWordOffsets(sentence.text, boundaries.map { it.text })
        val cues = ArrayList<TimedCue>(boundaries.size)
        for (i in boundaries.indices) {
            val local = offsets.getOrNull(i) ?: continue
            val start = (sentence.start + local.start).coerceAtLeast(0)
            val end = (sentence.start + local.end).coerceAtLeast(start)
            if (start >= end) continue
            val startSec = boundaries[i].offset.toDouble() / TICKS_PER_SECOND
            val endSec = if (boundaries[i].duration > 0) {
                startSec + boundaries[i].duration.toDouble() / TICKS_PER_SECOND
            } else {
                null
            }
            cues.add(TimedCue(startSec, endSec, start until end))
        }
        return cues
    }

    fun cueAtTime(cues: List<TimedCue>, seconds: Double): TimedCue? {
        if (cues.isEmpty()) return null
        var low = 0
        var high = cues.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startSec <= seconds) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (result >= 0) cues[result] else cues.firstOrNull()
    }

    /**
     * Absolute char range in the block for the word at [mediaTimeSec], or null
     * when boundaries/offsets are unavailable.
     */
    fun highlightRangeInBlock(
        sentence: Sentence,
        boundaries: List<EdgeWordBoundary>,
        mediaTimeSec: Double,
    ): IntRange? {
        val cues = cuesFromBoundaries(sentence, boundaries)
        return cueAtTime(cues, mediaTimeSec)?.charRange
    }

    /**
     * Map System TTS [onRangeStart] utterance-local [start, end) into the sentence's
     * absolute block range. Returns null when the range is empty or out of bounds.
     */
    fun rangeFromUtteranceChars(
        sentence: Sentence,
        start: Int,
        end: Int,
    ): IntRange? {
        if (start < 0 || end <= start) return null
        val localStart = start.coerceAtMost(sentence.text.length)
        val localEnd = end.coerceAtMost(sentence.text.length)
        if (localStart >= localEnd) return null
        val absStart = sentence.start + localStart
        val absEnd = sentence.start + localEnd
        if (absStart >= absEnd) return null
        return absStart until absEnd
    }
}
