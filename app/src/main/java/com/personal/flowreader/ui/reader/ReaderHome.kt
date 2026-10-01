package com.personal.flowreader.ui.reader

/**
 * Current-position geometry. The reader tracks one sentence (the spoken one while TTS plays,
 * otherwise the saved locus sentence); every "on screen?" check and every jump targets that
 * sentence, never its paragraph. All values share one vertical coordinate space.
 */

/** Which viewport edge the tracked sentence is beyond. */
internal enum class PlaybackPinEdge { Top, Bottom }

/** Vertical extent of the tracked sentence's lines. */
internal data class SentenceSpan(val sentenceIndex: Int, val top: Float, val bottom: Float)

internal object ReaderHome {
    /** Visible overlap (px) below which a sentence counts as off-screen. */
    const val MIN_VISIBLE_PX = 1f

    fun homeY(viewportTop: Float, viewportHeight: Float, home: Float): Float =
        viewportTop + viewportHeight * home

    /**
     * Scroll delta (positive moves content up) that puts the span's center on the home line.
     * A span taller than the viewport starts at the viewport top instead, so its first line shows.
     */
    fun scrollDelta(
        top: Float,
        bottom: Float,
        viewportTop: Float,
        viewportHeight: Float,
        home: Float,
    ): Float {
        if (bottom - top >= viewportHeight) return top - viewportTop
        return (top + bottom) / 2f - homeY(viewportTop, viewportHeight, home)
    }

    /** Edge the span is beyond, or null when any part of it is visible. */
    fun edgeOf(top: Float, bottom: Float, viewportTop: Float, viewportBottom: Float): PlaybackPinEdge? {
        val overlap = minOf(bottom, viewportBottom) - maxOf(top, viewportTop)
        if (overlap > MIN_VISIBLE_PX) return null
        return if (bottom <= viewportTop + MIN_VISIBLE_PX) PlaybackPinEdge.Top else PlaybackPinEdge.Bottom
    }

    /** Edge for an item outside the composed range [firstVisible]..[lastVisible]. */
    fun edgeOfItem(itemIndex: Int, firstVisible: Int, lastVisible: Int): PlaybackPinEdge? = when {
        itemIndex < firstVisible -> PlaybackPinEdge.Top
        itemIndex > lastVisible -> PlaybackPinEdge.Bottom
        else -> null
    }
}
