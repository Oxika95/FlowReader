package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.HomePosition
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
}
