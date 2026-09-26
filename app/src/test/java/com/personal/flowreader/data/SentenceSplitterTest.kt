package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoehnSentenceBreakTest {
    @Test
    fun doesNotBreakOnMister() {
        val parts = KoehnSentenceBreak.split("Mr. Smith went home.")
        assertEquals(listOf("Mr. Smith went home."), parts)
    }

    @Test
    fun breaksAfterRealSentenceFollowingTitle() {
        val parts = KoehnSentenceBreak.split("Dr. Jones left. Next came dawn.")
        assertEquals(2, parts.size)
        assertEquals("Dr. Jones left.", parts[0])
        assertEquals("Next came dawn.", parts[1])
    }

    @Test
    fun doesNotBreakOnEg() {
        val parts = KoehnSentenceBreak.split("See e.g. Apples and oranges.")
        assertEquals(1, parts.size)
        assertTrue(parts[0].contains("e.g."))
    }

    @Test
    fun doesNotBreakOnIe() {
        val parts = KoehnSentenceBreak.split("Use i.e. Exact wording only.")
        assertEquals(1, parts.size)
        assertTrue(parts[0].contains("i.e."))
    }

    @Test
    fun numericOnlyNoBeforeDigit() {
        val parts = KoehnSentenceBreak.split("No. 5 was the best option available today.")
        assertEquals(1, parts.size)
    }

    @Test
    fun breaksOnNoBeforeCapitalWord() {
        val parts = KoehnSentenceBreak.split("No. That was never the plan.")
        assertEquals(2, parts.size)
        assertEquals("No.", parts[0])
        assertEquals("That was never the plan.", parts[1])
    }

    @Test
    fun simplePeriodSplit() {
        val parts = KoehnSentenceBreak.split("Hello world. Next one.")
        assertEquals(listOf("Hello world.", "Next one."), parts)
    }

    @Test
    fun breaksAfterInc() {
        val parts = KoehnSentenceBreak.split("She works at Apple Inc. The stock rose.")
        assertEquals(2, parts.size)
        assertEquals("She works at Apple Inc.", parts[0])
        assertEquals("The stock rose.", parts[1])
    }

    @Test
    fun breaksAfterEtc() {
        val parts = KoehnSentenceBreak.split("He nodded etc. She continued.")
        assertEquals(2, parts.size)
        assertEquals("He nodded etc.", parts[0])
        assertEquals("She continued.", parts[1])
    }

    @Test
    fun breaksAfterSingleLetterSection() {
        val parts = KoehnSentenceBreak.split("See section A. The next part begins here.")
        assertEquals(2, parts.size)
        assertEquals("See section A.", parts[0])
        assertEquals("The next part begins here.", parts[1])
    }
}

class SentenceLengthNormalizerTest {
    @Test
    fun joinsShortsForwardUntilBand() {
        val parts = listOf(
            "Yes.",
            "No.",
            "Maybe the wind will change before nightfall arrives over the hills.",
            "And then we will see what comes next for everyone waiting.",
        )
        // First three crumbs + text should merge toward MIN without exceeding MAX.
        val out = SentenceLengthNormalizer.normalize(parts)
        assertTrue(out.size < parts.size)
        assertTrue(out.all { it.length <= SentenceLengthNormalizer.band().max })
    }

    @Test
    fun splitsLongAtCommaNearTarget() {
        val left = "a".repeat(70) + ", "
        val right = "b".repeat(70)
        val long = left + right // ~142 chars, above default max (100)
        assertTrue(long.length > SentenceLengthNormalizer.band().max)
        val out = SentenceLengthNormalizer.normalize(listOf(long))
        assertTrue(out.size >= 2)
        assertTrue(out.all { it.length <= SentenceLengthNormalizer.band().max })
        assertTrue(out[0].endsWith(",") || out[0].contains(","))
    }

    @Test
    fun splitsLongAtPeriodNearTarget() {
        // Missed Koehn break: one blob with an interior ". " over max → soft-split on period.
        val left = "a".repeat(60) + ". "
        val right = "B" + "b".repeat(60)
        val long = left + right
        assertTrue(long.length > SentenceLengthNormalizer.band().max)
        val out = SentenceLengthNormalizer.normalize(listOf(long))
        assertTrue(out.size >= 2)
        assertTrue(out.all { it.length <= SentenceLengthNormalizer.band().max })
        assertTrue(out[0].endsWith("."))
        assertTrue(out[1].startsWith("B"))
    }

    @Test
    fun refusesJoinThatExceedsMax() {
        val a = "a".repeat(40) // < MIN (50)
        val b = "b".repeat(70)
        val out = SentenceLengthNormalizer.normalize(listOf(a, b))
        // Would try join, but 40+1+70 > MAX (100) → keep separate
        assertEquals(2, out.size)
        assertEquals(a, out[0])
        assertEquals(b, out[1])
    }
}

class SentenceSplitterTest {
    @Test
    fun splitsOnPeriodWhenBothReachBand() {
        // Each side already in-band so join must not glue them.
        val a = "The cold river ran through the pine valley under grey morning skies."
        val b = "Travelers paused at the old stone bridge to rest and watch water."
        assertTrue("a=${a.length}", a.length in SentenceSplitter.MIN_CHARS..SentenceSplitter.MAX_CHARS)
        assertTrue("b=${b.length}", b.length in SentenceSplitter.MIN_CHARS..SentenceSplitter.MAX_CHARS)
        assertTrue("sum=${a.length + 1 + b.length}", a.length + 1 + b.length > SentenceSplitter.MAX_CHARS)
        val doc2 = TxtIngest.readText("t", "$a $b")
        val s = SentenceSplitter.split(doc2)
        assertEquals(2, s.size)
        assertTrue(s[0].text.startsWith("The cold river"))
        assertTrue(s[1].text.startsWith("Travelers"))
    }

    @Test
    fun joinsTinySentences() {
        val doc = TxtIngest.readText("t", "Hello world. Next one.")
        val s = SentenceSplitter.split(doc)
        // Both tiny → joined into one TTS segment.
        assertEquals(1, s.size)
        assertTrue(s[0].text.contains("Hello world."))
        assertTrue(s[0].text.contains("Next one."))
    }

    @Test
    fun keepsMisterTogether() {
        val doc = TxtIngest.readText("t", "Mr. Smith went home after the meeting ended early.")
        val s = SentenceSplitter.split(doc)
        assertEquals(1, s.size)
        assertTrue(s[0].text.startsWith("Mr. Smith"))
    }

    @Test
    fun keepsIncBreakThroughNormalizeWhenPartsStayLong() {
        val a = "After many years of careful work she finally left Apple Inc."
        val b = "The stock rose sharply when buyers flooded the open market floor."
        assertTrue("a=${a.length}", a.length >= SentenceSplitter.MIN_CHARS)
        assertTrue("b=${b.length}", b.length >= SentenceSplitter.MIN_CHARS)
        assertTrue(a.length + 1 + b.length > SentenceSplitter.MAX_CHARS)
        val doc = TxtIngest.readText("t", "$a $b")
        val s = SentenceSplitter.split(doc)
        assertEquals(2, s.size)
        assertTrue(s[0].text.endsWith("Inc."))
        assertTrue(s[1].text.startsWith("The stock"))
    }

    @Test
    fun indexAtCharOffset() {
        val a = "The cold river ran through the pine valley under grey morning skies."
        val b = "Travelers paused at the old stone bridge to rest and watch water."
        val text = "$a $b"
        val doc = TxtIngest.readText("t", text)
        val s = SentenceSplitter.split(doc)
        assertEquals(2, s.size)
        val i = SentenceSplitter.indexAt(s, Locus(0, 0, s[1].start))
        assertEquals(1, i)
    }

    @Test
    fun capsVeryLongBlock() {
        val long = "a".repeat(SentenceSplitter.MAX_SENTENCE_CHARS + 200)
        val doc = TxtIngest.readText("t", long)
        val s = SentenceSplitter.split(doc)
        assertTrue(s.size >= 2)
        assertTrue(s.all { it.text.length <= SentenceSplitter.MAX_SENTENCE_CHARS })
    }

    @Test
    fun coversFullBlockTextAcrossSplits() {
        val text = "Hello world. Next one. Third!"
        val doc = TxtIngest.readText("t", text)
        val s = SentenceSplitter.split(doc)
        assertEquals(0, s.first().start)
        // Joined segment should still map over the spoken span.
        assertTrue(s.last().end >= text.length - 1)
    }
}
