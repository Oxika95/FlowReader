package com.personal.flowreader.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One reading position to store in row [rowKey] of [session]'s table ([ReadingSessionId.domain]).
 * [chapterIndex] is in that row's own indices (plugin books: ToC index).
 */
data class ProgressUpdate(
    val session: ReadingSessionId,
    val rowKey: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int,
    val fraction: Float,
    val at: Long,
    val anchorText: String = "",
    val chapterHref: String = "",
    val source: PositionSource = PositionSource.Reader,
    val chapterFinished: Boolean = false,
) {
    val domain: PositionDomain get() = session.domain

    companion object {
        /**
         * The one way to build a seek to a chapter's start (or [blockIndex] / [charOffset] inside it) for
         * row [rowKey] of [domain]. [chapterCount] sets the progress fraction; 0 when unknown.
         */
        fun chapterSeek(
            domain: PositionDomain,
            rowKey: String,
            chapterIndex: Int,
            chapterCount: Int,
            source: PositionSource,
            chapterHref: String = "",
            at: Long = System.currentTimeMillis(),
            blockIndex: Int = 0,
            charOffset: Int = 0,
            anchorText: String = "",
            /** When set (ebook locus sync), overrides the chapter-index fraction. */
            fraction: Float? = null,
        ) = ProgressUpdate(
            session = ReadingSessionId(domain, rowKey),
            rowKey = rowKey,
            chapterIndex = chapterIndex,
            blockIndex = blockIndex,
            charOffset = charOffset,
            fraction = fraction?.coerceIn(0f, 1f)
                ?: if (chapterCount > 0) chapterIndex.toFloat() / chapterCount else 0f,
            at = at,
            anchorText = anchorText,
            chapterHref = chapterHref,
            source = source,
        )
    }

    fun toPosition() = ReadingPosition(
        chapterIndex = chapterIndex,
        chapterHref = chapterHref,
        blockIndex = blockIndex,
        charOffset = charOffset,
        anchorText = anchorText,
        fraction = fraction.coerceIn(0f, 1f),
        positionAt = at,
        positionSessionAt = session.openedAt,
        positionSource = source.name,
        chapterFinished = chapterFinished,
    )
}

/** Maps a locus in a session's reading window to the row it belongs to. Owned by the session. */
fun interface ProgressLocator {
    fun locate(locus: Locus, at: Long): ProgressUpdate
}

/** Writes a position into the table and row the update names; false when the row is gone. */
fun interface PositionStore {
    suspend fun write(update: ProgressUpdate): Boolean
}

/**
 * The only writer of reading positions (reader, TTS with the reader closed, plugin seeks, sync).
 * App-scoped so pending writes outlive the reader. Serialized: keeps the latest update per row
 * and writes in order.
 */
class ProgressWriter(
    private val store: PositionStore,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
    private val log: (ProgressUpdate, Boolean) -> Unit = { _, _ -> },
    private val onFailed: (ProgressUpdate, Throwable) -> Unit = { _, _ -> },
    private val onWritten: suspend (ProgressUpdate) -> Unit = {},
) {
    private val pending = LinkedHashMap<String, ProgressUpdate>()
    private val writeLock = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in wake) {
                delay(debounceMs)
                drain()
            }
        }
    }

    fun submit(update: ProgressUpdate, flush: Boolean = false) {
        if (update.rowKey.isBlank()) return
        val key = "${update.domain}:${update.rowKey}"
        synchronized(pending) {
            val current = pending[key]
            if (current == null || current.at <= update.at) pending[key] = update
        }
        if (flush) scope.launch { drain() } else wake.trySend(Unit)
    }

    /** [submit] with a flush, then wait until the write has happened. The row must already exist. */
    suspend fun submitAndDrain(update: ProgressUpdate) {
        submit(update)
        drain()
    }

    /**
     * Write everything pending now. A store failure is reported to [onFailed] and that update is
     * dropped; the app scope has no exception handler, so it must not escape.
     */
    suspend fun drain() {
        writeLock.withLock {
            val batch = synchronized(pending) {
                pending.values.toList().also { pending.clear() }
            }
            for (update in batch) {
                val written = try {
                    store.write(update)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    runCatching { onFailed(update, e) }
                    continue
                }
                runCatching { log(update, written) }
                if (written) runCatching { onWritten(update) }
            }
        }
    }
}
