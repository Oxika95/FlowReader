package com.personal.flowreader.ui.reader

/** When the reader may write its position. Pure. */
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
}
