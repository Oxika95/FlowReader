package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.BookPlace
import com.personal.flowreader.data.ReaderItem

/**
 * Current-position geometry. The reader tracks one sentence (the spoken one while TTS plays,
 * otherwise the saved BookPlace sentence); every "on screen?" check and every jump targets that
 * sentence, never its paragraph. All values share one vertical coordinate space.
 */

/** Which viewport edge the tracked sentence is beyond. */
internal enum class PlaybackPinEdge { Top, Bottom }

/** Vertical extent of the tracked sentence's lines, measured on the paragraph with [blockId]. */
internal data class SentenceSpan(val blockId: String, val sentenceIndex: Int, val top: Float, val bottom: Float)

internal object ReaderHome {
    /** Visible overlap (px) below which a sentence counts as off-screen. */
    const val MIN_VISIBLE_PX = 1f

    fun homeY(viewportTop: Float, viewportHeight: Float, home: Float): Float =
        viewportTop + viewportHeight * home

    /**
     * LM [scrollToPositionWithOffset] offset (from the padding edge) that puts the row top
     * on the home marker. Marker is from the box top; LM offset starts after [padTop].
     */
    fun homePadOffset(viewportHeight: Float, home: Float, padTop: Int): Int {
        if (viewportHeight <= 0f) return 0
        return (viewportHeight * home - padTop).toInt()
    }

    /**
     * Scroll delta (positive moves content up) that puts [targetY] on the home line —
     * same Y the home marker uses.
     */
    fun deltaToHome(
        targetY: Float,
        viewportTop: Float,
        viewportHeight: Float,
        home: Float,
    ): Float = targetY - homeY(viewportTop, viewportHeight, home)

    /**
     * Scroll delta that puts the span's center on the home line (TTS follow).
     * A span taller than the viewport starts at the viewport top instead.
     */
    fun scrollDelta(
        top: Float,
        bottom: Float,
        viewportTop: Float,
        viewportHeight: Float,
        home: Float,
    ): Float {
        if (bottom - top >= viewportHeight) return top - viewportTop
        return deltaToHome((top + bottom) / 2f, viewportTop, viewportHeight, home)
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

    /**
     * First..last chapter among visible block ids that resolve in [chapterByBlockId].
     * Null when no visible id matches.
     */
    fun visibleChapterRange(
        visibleKeys: List<Any>,
        chapterByBlockId: Map<String, Int>,
    ): IntRange? {
        var first: Int? = null
        var last: Int? = null
        for (key in visibleKeys) {
            val id = key as? String ?: continue
            val chapter = chapterByBlockId[id] ?: continue
            if (first == null) first = chapter
            last = chapter
        }
        val lo = first ?: return null
        val hi = last ?: return null
        return lo..hi
    }

    /**
     * Adapter index for [place]'s chapter+block in the current window, or -1 when that
     * paragraph is not loaded. Includes synthetic chapter-title rows (`blockIndex == -1`).
     */
    fun placeToAdapterIndex(items: List<ReaderItem>, place: BookPlace): Int =
        items.indexOfFirst {
            it.chapterIndex == place.chapterIndex && it.blockIndex == place.blockIndex
        }

    /** @deprecated Use [placeToAdapterIndex]. */
    fun locusToListIndex(items: List<ReaderItem>, locus: BookPlace): Int =
        placeToAdapterIndex(items, locus)
}
