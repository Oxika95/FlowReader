package com.personal.flowreader.data

/**
 * A stored reading position, embedded in every table that owns one (Library, Queue, plugin
 * books). A position save writes these columns and nothing else.
 */
data class ReadingPosition(
    /** `ChapterSource` index (EPUB spine entry, TXT segment, plugin ToC index). */
    val chapterIndex: Int = 0,
    /** Stable key of the chapter (EPUB entry path, plugin chapter URL), checked before [chapterIndex]. */
    val chapterHref: String = "",
    val blockIndex: Int = 0,
    val charOffset: Int = 0,
    /** Text at the locus, to re-find it if indices drift (see `LocusAnchor`). */
    val anchorText: String = "",
    /** 0–1 whole-book progress (header bar, library cards). */
    val fraction: Float = 0f,
    /** When this position was written. */
    val positionAt: Long = 0L,
    /** `openedAt` of the reading session that wrote it. */
    val positionSessionAt: Long = 0L,
    /** [PositionSource] name of the writer. */
    val positionSource: String = "",
) {
    val hasProgress: Boolean get() = chapterIndex > 0 || blockIndex > 0 || charOffset > 0
}

/** Who wrote a position. */
enum class PositionSource { Reader, Tts, PluginSeek, Sync, Migration }

/** Which table a position belongs to. */
enum class PositionDomain { Library, Queue, Plugin }

/**
 * Identity of one reading session (a reader launch, or a standalone plugin seek / sync write).
 * [domain] picks the table and [key] the row; the Queue stream has no single row ([key] blank),
 * each of its writes names the Queue item under the cursor.
 */
data class ReadingSessionId(
    val domain: PositionDomain,
    val key: String,
    val openedAt: Long = System.currentTimeMillis(),
) {
    override fun toString(): String {
        val d = domain.name.lowercase()
        return if (key.isEmpty()) "$d@$openedAt" else "$d:$key@$openedAt"
    }
}
