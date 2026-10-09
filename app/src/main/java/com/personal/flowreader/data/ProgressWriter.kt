package com.personal.flowreader.data

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
) {
    val domain: PositionDomain get() = session.domain

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

    /** Write everything pending now. */
    suspend fun drain() {
        writeLock.withLock {
            val batch = synchronized(pending) {
                pending.values.toList().also { pending.clear() }
            }
            for (update in batch) {
                val written = store.write(update)
                runCatching { log(update, written) }
                if (written) runCatching { onWritten(update) }
            }
        }
    }
}
