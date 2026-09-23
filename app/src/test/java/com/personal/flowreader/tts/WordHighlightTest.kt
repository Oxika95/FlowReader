package com.personal.flowreader.tts

import com.personal.flowreader.data.Sentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordHighlightTest {
    private val sentence = Sentence(
        chapterIndex = 0,
        blockIndex = 0,
        start = 10,
        end = 30,
        text = "Hello world again",
    )

    @Test
    fun cuesFromBoundariesMapToBlockRanges() {
        val boundaries = listOf(
            EdgeWordBoundary(0L, 1_000_000L, "Hello"),
            EdgeWordBoundary(5_000_000L, 1_000_000L, "world"),
            EdgeWordBoundary(10_000_000L, 1_000_000L, "again"),
        )
        val cues = WordHighlight.cuesFromBoundaries(sentence, boundaries)
        assertEquals(3, cues.size)
        assertEquals(10 until 15, cues[0].charRange)
        assertEquals(16 until 21, cues[1].charRange)
        assertEquals(0.0, cues[0].startSec, 1e-9)
        assertEquals(0.5, cues[1].startSec, 1e-9)
    }

    @Test
    fun cueAtTimePicksLatestStarted() {
        val cues = listOf(
            WordHighlight.TimedCue(0.0, 0.4, 10 until 15),
            WordHighlight.TimedCue(0.5, 0.9, 16 until 21),
        )
        assertEquals(10 until 15, WordHighlight.cueAtTime(cues, 0.2)?.charRange)
        assertEquals(16 until 21, WordHighlight.cueAtTime(cues, 0.6)?.charRange)
    }

    @Test
    fun cueAtTimeRespectsSyncOffsetViaCaller() {
        val cues = listOf(
            WordHighlight.TimedCue(0.0, null, 10 until 15),
            WordHighlight.TimedCue(1.0, null, 16 until 21),
        )
        // Caller applies offset: heard 0.95 + offset 0.1 = 1.05 → second cue
        val heard = 0.95
        val offsetSec = 0.1
        assertEquals(16 until 21, WordHighlight.cueAtTime(cues, heard + offsetSec)?.charRange)
    }

    @Test
    fun rangeFromUtteranceCharsMapsIntoBlock() {
        assertEquals(10 until 15, WordHighlight.rangeFromUtteranceChars(sentence, 0, 5))
        assertEquals(16 until 21, WordHighlight.rangeFromUtteranceChars(sentence, 6, 11))
        assertNull(WordHighlight.rangeFromUtteranceChars(sentence, 5, 5))
        assertNull(WordHighlight.rangeFromUtteranceChars(sentence, -1, 2))
    }

    @Test
    fun unmatchedBoundaryWordsSkipped() {
        val boundaries = listOf(
            EdgeWordBoundary(0L, 1_000_000L, "Hello"),
            EdgeWordBoundary(5_000_000L, 1_000_000L, "FILTERED"),
            EdgeWordBoundary(10_000_000L, 1_000_000L, "world"),
        )
        val cues = WordHighlight.cuesFromBoundaries(sentence, boundaries)
        assertEquals(2, cues.size)
        assertEquals(10 until 15, cues[0].charRange)
        assertEquals(16 until 21, cues[1].charRange)
    }
}
