package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.HomePosition
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ReaderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderHomeTest {
    @Test
    fun homeYIsFractionOfViewport() {
        assertEquals(500f, ReaderHome.homeY(viewportTop = 0f, viewportHeight = 1000f, home = 0.5f), 0f)
        assertEquals(350f, ReaderHome.homeY(viewportTop = 50f, viewportHeight = 1000f, home = 0.3f), 0f)
    }

    @Test
    fun scrollDeltaCentersSentenceOnHome() {
        // Sentence 800..840 (center 820), home at 500 → scroll content up by 320.
        assertEquals(320f, ReaderHome.scrollDelta(800f, 840f, 0f, 1000f, 0.5f), 0f)
        // Above home → negative delta scrolls content down.
        assertEquals(-380f, ReaderHome.scrollDelta(100f, 140f, 0f, 1000f, 0.5f), 0f)
    }

    @Test
    fun scrollDeltaFollowsHomePosition() {
        val atTop = ReaderHome.scrollDelta(480f, 520f, 0f, 1000f, 0.2f)
        val atBottom = ReaderHome.scrollDelta(480f, 520f, 0f, 1000f, 0.8f)
        assertEquals(300f, atTop, 0f)
        assertEquals(-300f, atBottom, 0f)
    }

    @Test
    fun tallSentenceStartsAtViewportTop() {
        assertEquals(200f, ReaderHome.scrollDelta(300f, 1500f, 100f, 1000f, 0.5f), 0f)
    }

    @Test
    fun visibleSentenceHasNoEdge() {
        assertNull(ReaderHome.edgeOf(400f, 440f, 0f, 1000f))
        // Partially visible at either edge still counts as on-screen.
        assertNull(ReaderHome.edgeOf(-20f, 20f, 0f, 1000f))
        assertNull(ReaderHome.edgeOf(980f, 1020f, 0f, 1000f))
    }

    @Test
    fun offscreenSentenceReportsItsEdge() {
        assertEquals(PlaybackPinEdge.Top, ReaderHome.edgeOf(-80f, -40f, 0f, 1000f))
        assertEquals(PlaybackPinEdge.Bottom, ReaderHome.edgeOf(1040f, 1080f, 0f, 1000f))
        // Sentence in the same paragraph but scrolled past: paragraph visible, sentence not.
        assertEquals(PlaybackPinEdge.Top, ReaderHome.edgeOf(-200f, -160f, 0f, 1000f))
    }

    @Test
    fun itemOutsideComposedRange() {
        assertEquals(PlaybackPinEdge.Top, ReaderHome.edgeOfItem(2, 5, 9))
        assertEquals(PlaybackPinEdge.Bottom, ReaderHome.edgeOfItem(12, 5, 9))
        assertNull(ReaderHome.edgeOfItem(7, 5, 9))
    }

    @Test
    fun homePositionIsClamped() {
        assertEquals(HomePosition.MIN, HomePosition.coerce(0f), 0f)
        assertEquals(HomePosition.MAX, HomePosition.coerce(1f), 0f)
        assertEquals(0.4f, HomePosition.coerce(0.4f), 0f)
    }

    @Test
    fun visibleChapterRangeFromMatchingKeys() {
        val byId = mapOf("c4-0" to 4, "c4-1" to 4, "c5-0" to 5)
        assertEquals(4..5, ReaderHome.visibleChapterRange(listOf("c4-1", "c5-0"), byId))
        assertEquals(4..4, ReaderHome.visibleChapterRange(listOf("c4-0", "c4-1"), byId))
    }

    @Test
    fun visibleChapterRangeNullWhenKeysAbsent() {
        // After front trim, layoutInfo may still list removed block ids — skip, do not use index.
        val byId = mapOf("c5-0" to 5, "c6-0" to 6)
        assertNull(ReaderHome.visibleChapterRange(listOf("c3-0", "c4-0"), byId))
        assertNull(ReaderHome.visibleChapterRange(emptyList(), byId))
    }

    @Test
    fun visibleChapterRangeIgnoresUnresolvedKeys() {
        val byId = mapOf("c5-0" to 5, "c6-0" to 6)
        // Mix of stale and live keys: only live ones count.
        assertEquals(5..6, ReaderHome.visibleChapterRange(listOf("c4-0", "c5-0", "c6-0"), byId))
    }

    @Test
    fun locusToListIndexFindsChapterAndBlock() {
        val items = listOf(
            ReaderItem(4, ReaderItem.CHAPTER_TITLE_BLOCK, Block("t4", BlockKind.Heading, "Ch 4")),
            ReaderItem(4, 0, Block("c4-0", BlockKind.Paragraph, "a")),
            ReaderItem(4, 1, Block("c4-1", BlockKind.Paragraph, "b")),
            ReaderItem(5, 0, Block("c5-0", BlockKind.Paragraph, "c")),
        )
        assertEquals(0, ReaderHome.locusToListIndex(items, Locus(4, ReaderItem.CHAPTER_TITLE_BLOCK, 0)))
        assertEquals(2, ReaderHome.locusToListIndex(items, Locus(4, 1, 3)))
        assertEquals(3, ReaderHome.locusToListIndex(items, Locus(5, 0, 0)))
    }

    @Test
    fun locusToListIndexMissWhenNotLoaded() {
        val items = listOf(ReaderItem(5, 0, Block("c5-0", BlockKind.Paragraph, "c")))
        assertEquals(-1, ReaderHome.locusToListIndex(items, Locus(4, 0, 0)))
        assertEquals(-1, ReaderHome.locusToListIndex(emptyList(), Locus(5, 0, 0)))
    }
}
