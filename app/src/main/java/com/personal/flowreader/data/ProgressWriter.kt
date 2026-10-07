package com.personal.flowreader.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One reading position to store. [chapterIndex] is absolute (plugin books: ToC index).
 * [fraction] null keeps the stored reading progress.
 */
data class ProgressUpdate(
    val bookId: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int,
    val fraction: Float?,
    val at: Long,
)

/**
 * The only writer of reading positions (reader, TTS with the reader closed, plugin seeks).
 * App-scoped so pending writes outlive the reader. Per book, a write older than the last one
 * stored is dropped, so a late flush can never rewind a newer position.
 */
class ProgressWriter(
    private val dao: ProgressDao,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
    private val onWritten: suspend (ProgressUpdate) -> Unit = {},
) {
    private val pending = HashMap<String, ProgressUpdate>()
    private val lastWrittenAt = HashMap<String, Long>()
    private val writeLock = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val meters = HashMap<String, (Locus) -> Float>()

    init {
        scope.launch {
            for (signal in wake) {
                delay(debounceMs)
                drain()
            }
        }
    }

    /** Fraction source for TTS-driven writes; replaced whenever the reader (re)loads [bookId]. */
    fun setMeter(bookId: String, meter: ((Locus) -> Float)?) {
        synchronized(meters) {
            if (meter == null) meters.remove(bookId) else meters[bookId] = meter
        }
    }

    fun fractionFor(bookId: String, locus: Locus): Float? =
        synchronized(meters) { meters[bookId] }?.invoke(locus)?.coerceIn(0f, 1f)

    fun submit(update: ProgressUpdate, flush: Boolean = false) {
        if (update.bookId.isBlank()) return
        synchronized(pending) {
            val current = pending[update.bookId]
            if (current == null || current.at <= update.at) pending[update.bookId] = update
        }
        if (flush) scope.launch { drain() } else wake.trySend(Unit)
    }

    /** Write everything pending now. */
    suspend fun drain() {
        writeLock.withLock {
            val batch = synchronized(pending) {
                pending.values.toList().also { pending.clear() }
            }
            for (update in batch) write(update)
        }
    }

    private suspend fun write(update: ProgressUpdate) {
        if ((lastWrittenAt[update.bookId] ?: Long.MIN_VALUE) > update.at) return
        val row = dao.get(update.bookId) ?: return
        dao.upsert(
            row.copy(
                chapterIndex = update.chapterIndex,
                blockIndex = update.blockIndex,
                charOffset = update.charOffset,
                readingProgress = update.fraction ?: row.readingProgress,
                updatedAt = update.at,
            ),
        )
        lastWrittenAt[update.bookId] = update.at
        runCatching { onWritten(update) }
    }
}
