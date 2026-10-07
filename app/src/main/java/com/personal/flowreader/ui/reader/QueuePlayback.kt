package com.personal.flowreader.ui.reader

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.tts.SynthDebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Playback moved from Queue row [fromQueId] to [queId] (book [bookId]). */
internal data class QueueAdvance(val fromQueId: String, val bookId: String, val queId: String)

/**
 * Plays the Queue through: when TTS finishes a Queue document, marks it done and starts the next
 * undone item from its beginning, with or without the reader open. An open reader follows via
 * [advanced] and reuses the session started here.
 */
internal class QueuePlayback(private val flow: FlowApp) {
    /** Book id to Queue row id of the document TTS is reading; null when not from the Queue. */
    @Volatile
    private var current: Pair<String, String>? = null

    private val _advanced = MutableSharedFlow<QueueAdvance>(extraBufferCapacity = 1)
    val advanced: SharedFlow<QueueAdvance> = _advanced

    fun start() {
        flow.appScope.launch {
            flow.tts.bookFinished.collect { advance() }
        }
    }

    /** The reader opened [bookId], as Queue row [queId] or (null) outside the Queue. */
    fun track(bookId: String, queId: String?) {
        current = queId?.let { bookId to it }
    }

    private suspend fun advance() {
        val (bookId, queId) = current ?: return
        if (flow.tts.state.value.bookId != bookId) return
        val next = withContext(Dispatchers.IO) {
            val row = flow.catalog.getQue(queId) ?: return@withContext null
            flow.catalog.markQueDone(queId)
            flow.catalog.nextUndoneQue(row.sortOrder)
        }
        if (next == null) {
            current = null
            return
        }
        val nextBookId = next.progress.bookId
        try {
            play(nextBookId)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            current = null
            SynthDebugLog.appendError("Queue: ${t.message ?: "could not open next item"}")
            return
        }
        current = nextBookId to next.item.id
        _advanced.tryEmit(QueueAdvance(queId, nextBookId, next.item.id))
    }

    /** Open [bookId] the way the reader does (same filters, so the reader can reuse it) and play. */
    private suspend fun play(bookId: String) {
        val row = flow.db.progress().get(bookId) ?: throw IllegalStateException("item not found")
        val book = withContext(Dispatchers.IO) {
            ReaderBook.local(ChapterSource.open(flow.catalog.materialize(row), row.title))
        }
        val global = flow.settings.globalFiltersOnce()
        val groups = flow.settings.groupFiltersOnce()
        val local = TextFilters.decodeRules(flow.db.bookFilters().get(bookId)?.rulesJson)
        val rules = TextFilters.merge(global, groups, local)
        val clip = flow.tts.state.value
        val session = ReaderSessions.open(bookId, book, 0, rules, clip.clipTargetChars, clip.clipFlexChars)
        flow.progress.setLocator(bookId, ReaderSessions.locator(bookId, book, session))
        withContext(Dispatchers.Main) {
            flow.tts.setSpeechFilters(global, groups, local)
            flow.tts.attach(session, ReaderSessions.readable(session.window.value, Locus()))
            if (session.window.value.table.isNotEmpty()) flow.tts.play()
        }
    }
}
