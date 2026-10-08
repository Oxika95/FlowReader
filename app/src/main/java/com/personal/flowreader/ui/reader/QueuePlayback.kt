package com.personal.flowreader.ui.reader

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.tts.SynthDebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The open Queue stream after a Queue table change; [session] is null once the Queue is empty. */
internal class QueueUpdate(
    val change: QueueChange,
    val previous: QueueBook,
    val ref: QueueStreamRef,
    val session: ReadingSession?,
)

/**
 * Owner of the Queue stream TTS is reading ([QueueBook], session id [QueueBook.ID]), with or
 * without the reader open. Follows the Queue table live: appended rows and Done flags are swapped
 * into the open session; reordered or removed rows rebuild it at the same text (TTS restarts the
 * sentence). An item is marked done when playback moves on into the next one.
 */
internal class QueuePlayback(private val flow: FlowApp) {
    private class Active(val ref: QueueStreamRef, val session: ReadingSession)

    @Volatile
    private var active: Active? = null

    /** Stream of the session TTS is attached to (stale once TTS reads a regular book). */
    val stream: QueueBook? get() = active?.ref?.book

    @Volatile
    private var spokenSegment = -1

    private val applyLock = Mutex()

    private val _updates = MutableSharedFlow<QueueUpdate>(extraBufferCapacity = 4)

    /** The open stream changed; an open Queue reader follows it. */
    val updates: SharedFlow<QueueUpdate> = _updates

    @OptIn(FlowPreview::class)
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
        flow.appScope.launch {
            flow.db.que().observeAll()
                .map { rows -> rows.map { it.id to it.done } }
                .distinctUntilChanged()
                .debounce(ROWS_DEBOUNCE_MS)
                .collect { applyRows() }
        }
    }

    /** The open stream when TTS is still attached to it (a reopening reader reuses both). */
    fun current(): Pair<QueueStreamRef, ReadingSession>? =
        active?.takeIf { flow.tts.attachedSession() === it.session }?.let { it.ref to it.session }

    /** [session] over [ref] was just attached to TTS (new, or reused while it keeps playing). */
    fun attach(ref: QueueStreamRef, session: ReadingSession) {
        active = Active(ref, session)
        spokenSegment = flow.tts.state.value.sentence?.chapterIndex?.let(ref.book::segmentIndexOf) ?: -1
    }

    private suspend fun onSpokenChapter(chapter: Int) {
        val queue = stream ?: return
        val seg = queue.segmentIndexOf(chapter)
        val previous = spokenSegment
        spokenSegment = seg
        if (previous >= 0 && seg == previous + 1) markDone(queue.segments[previous].queId)
    }

    /** Rebuild from the Queue table and apply the difference to the open stream. */
    private suspend fun applyRows() = applyLock.withLock {
        val a = active ?: return@withLock
        if (flow.tts.attachedSession() !== a.session) {
            active = null
            return@withLock
        }
        try {
            val old = a.ref.book
            val fresh = QueueStreams.build(flow, reuse = old)
            when (val change = QueueChange.classify(old, fresh)) {
                QueueChange.Same -> Unit
                QueueChange.DoneOnly, QueueChange.Appended -> {
                    a.ref.book = fresh
                    a.session.updateSource(fresh.reader.chapterTitles, a.ref.contentKey)
                    flow.progress.setLocator(QueueBook.ID, fresh.locator(a.session))
                    _updates.emit(QueueUpdate(change, old, a.ref, a.session))
                }
                QueueChange.Edited -> rebuild(a, old, fresh)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SynthDebugLog.appendError("Queue: ${t.message ?: "could not update"}")
        }
    }

    /** Rows moved or went away: open the new stream at the spoken text (or the next row). */
    private suspend fun rebuild(a: Active, old: QueueBook, fresh: QueueBook) {
        val ref = QueueStreamRef(fresh, a.ref.global, a.ref.groups)
        if (fresh.segments.isEmpty()) {
            withContext(Dispatchers.Main) { flow.tts.stop() }
            active = null
            _updates.emit(QueueUpdate(QueueChange.Edited, old, ref, null))
            return
        }
        val spoken = flow.tts.state.value.sentence
            ?.takeIf { flow.tts.state.value.bookId == QueueBook.ID }
            ?.let { Locus(it.chapterIndex, it.blockIndex, it.start) }
        val target = spoken?.let { fresh.remap(it, old) } ?: Locus(0, 0, 0)
        val session = QueueStreams.open(flow, ref, target.chapterIndex)
        flow.progress.setLocator(QueueBook.ID, fresh.locator(session))
        withContext(Dispatchers.Main) {
            flow.tts.setSpeechFilters(ref.global, ref.groups, fresh.segmentAt(target.chapterIndex)?.local.orEmpty())
            flow.tts.attach(session, ReaderSessions.readable(session.window.value, target))
            attach(ref, session)
        }
        _updates.emit(QueueUpdate(QueueChange.Edited, old, ref, session))
    }

    /**
     * End of the stream: mark the last item done. Rows appended just before the end may not be
     * applied yet; apply them and keep playing into the first new chapter.
     */
    private suspend fun onFinished() {
        if (flow.tts.state.value.bookId != QueueBook.ID) return
        val a = active ?: return
        val finished = a.ref.book
        val last = finished.segments.lastOrNull() ?: return
        markDone(last.queId)
        applyRows()
        val now = active?.takeIf { it.session === a.session } ?: return
        val next = finished.chapterCount
        if (now.session.count <= next) return
        try {
            val clip = flow.tts.state.value
            now.session.loadAdjacent(next, clip.clipTargetChars, clip.clipFlexChars)
            withContext(Dispatchers.Main) {
                flow.tts.jumpTo(Locus(next, 0, 0))
                flow.tts.play()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SynthDebugLog.appendError("Queue: ${t.message ?: "could not continue"}")
        }
    }

    private suspend fun markDone(queId: String) {
        withContext(Dispatchers.IO) { flow.catalog.markQueDone(queId) }
    }

    private companion object {
        const val ROWS_DEBOUNCE_MS = 150L
    }
}
