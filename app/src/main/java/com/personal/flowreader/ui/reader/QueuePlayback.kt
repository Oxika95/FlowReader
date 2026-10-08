package com.personal.flowreader.ui.reader

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.Locus
import com.personal.flowreader.tts.SynthDebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bookkeeping for the Queue stream TTS is reading ([QueueBook], session id [QueueBook.ID]), with
 * or without the reader open: an item is marked done when playback moves on into the next one;
 * at the end of the stream, rows added since it was built continue in a new stream.
 */
internal class QueuePlayback(private val flow: FlowApp) {
    /** Stream of the session TTS is attached to (stale once TTS reads a regular book). */
    @Volatile
    var stream: QueueBook? = null
        private set

    @Volatile
    private var spokenSegment = -1

    private val _streamChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Playback moved to a rebuilt stream; an open Queue reader reloads onto it. */
    val streamChanged: SharedFlow<Unit> = _streamChanged

    fun start() {
        flow.appScope.launch {
            flow.tts.bookFinished.collect { onFinished() }
        }
        flow.appScope.launch {
            flow.tts.state
                .map { s -> s.sentence?.chapterIndex?.takeIf { s.bookId == QueueBook.ID } }
                .distinctUntilChanged()
                .collect { chapter -> if (chapter != null) onSpokenChapter(chapter) }
        }
    }

    /** [queue]'s session was just attached to TTS (new, or reused while it keeps playing). */
    fun attach(queue: QueueBook) {
        stream = queue
        spokenSegment = flow.tts.state.value.sentence?.chapterIndex?.let(queue::segmentIndexOf) ?: -1
    }

    private suspend fun onSpokenChapter(chapter: Int) {
        val queue = stream ?: return
        val seg = queue.segmentIndexOf(chapter)
        val previous = spokenSegment
        spokenSegment = seg
        if (previous >= 0 && seg == previous + 1) markDone(queue.segments[previous].queId)
    }

    private suspend fun onFinished() {
        if (flow.tts.state.value.bookId != QueueBook.ID) return
        val old = stream ?: return
        val last = old.segments.lastOrNull() ?: return
        markDone(last.queId)
        try {
            val fresh = QueueStreams.build(flow)
            val known = old.segments.mapTo(HashSet()) { it.queId }
            val after = fresh.indexOfQue(last.queId)
            if (after < 0) return
            val next = (after + 1 until fresh.segments.size)
                .firstOrNull { fresh.segments[it].queId !in known && !fresh.segments[it].done }
                ?: return
            play(fresh, fresh.baseOf(next))
            _streamChanged.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SynthDebugLog.appendError("Queue: ${t.message ?: "could not continue"}")
        }
    }

    private suspend fun play(queue: QueueBook, chapter: Int) {
        val global = flow.settings.globalFiltersOnce()
        val groups = flow.settings.groupFiltersOnce()
        val session = QueueStreams.open(flow, queue, chapter, global, groups)
        flow.progress.setLocator(QueueBook.ID, queue.locator(session))
        withContext(Dispatchers.Main) {
            flow.tts.setSpeechFilters(global, groups, queue.segmentAt(chapter)?.local.orEmpty())
            flow.tts.attach(session, ReaderSessions.readable(session.window.value, Locus(chapter, 0, 0)))
            attach(queue)
            if (session.window.value.table.isNotEmpty()) flow.tts.play()
        }
    }

    private suspend fun markDone(queId: String) {
        withContext(Dispatchers.IO) { flow.catalog.markQueDone(queId) }
    }
}
