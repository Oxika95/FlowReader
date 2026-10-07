package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.Locus

/** When the reader may write its position, and what fraction it reports. Pure. */
internal object ReaderProgressPolicy {
    /**
     * Only a loaded, error-free reader whose position actually moved may write: the placeholder
     * locus shown while loading (or after a failure) must never replace saved progress.
     */
    fun mayPersist(
        bookId: String,
        hasDoc: Boolean,
        loading: Boolean,
        error: String?,
        positionMoved: Boolean,
    ): Boolean = bookId.isNotBlank() && hasDoc && !loading && error == null && positionMoved

    /** Block position over all blocks of [doc] (0–1). */
    fun fraction(doc: BookDoc, locus: Locus): Float {
        val items = doc.items
        if (items.size <= 1) return 0f
        return (locus.flatIndex(doc).toFloat() / items.lastIndex).coerceIn(0f, 1f)
    }
}
