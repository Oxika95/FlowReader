package com.personal.flowreader.ui.reader

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.PositionDomain
import com.personal.flowreader.data.ProgressLocator
import com.personal.flowreader.data.QueueItemEntity
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.data.ReadingSessionId
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.tts.SynthDebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds the Queue stream from the Queue rows and opens reading sessions over it. */
internal object QueueStreams {
    /**
     * Every Queue row in order; rows whose book can't be opened are left out (and logged). Rows
     * already in [reuse] keep their opened book and Local rules.
     */
    suspend fun build(flow: FlowApp, reuse: QueueBook? = null): QueueBook = withContext(Dispatchers.IO) {
        val items = flow.catalog.listQue()
        val known = reuse?.segments?.associateBy { it.queId }.orEmpty()
        QueueBook(
            items.mapNotNull { item ->
                val old = known[item.queId]?.takeIf { it.bookId == item.bookId }
                if (old != null) {
                    QueueSegment(old.queId, old.bookId, item.title, item.done, old.storedPath, old.book, old.local)
                } else {
                    segment(flow, item)
                }
            },
        )
    }

    private suspend fun segment(flow: FlowApp, item: QueueItemEntity): QueueSegment? {
        val book = try {
            if (flow.pluginBooks.isPluginBook(item.bookId)) {
                ReaderBook.plugin(flow.pluginBooks, flow.pluginBooks.openStory(item.bookId))
            } else {
                ReaderBook.local(ChapterSource.open(flow.catalog.materialize(item), item.title))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SynthDebugLog.appendError("Queue: skipped \"${item.title}\" (${t.message ?: t.javaClass.simpleName})")
            return null
        }
        if (book.chapterCount == 0) return null
        return QueueSegment(
            queId = item.queId,
            bookId = item.bookId,
            title = item.title,
            done = item.done,
            storedPath = item.storedPath,
            book = book,
            local = TextFilters.decodeRules(item.localFilters.ifBlank { null }),
        )
    }

    /**
     * Session over [ref]: loads read [QueueStreamRef.book], so appended rows show up in place.
     * Positions map through [ref]'s current book, so they always land on that stream's items.
     */
    suspend fun open(flow: FlowApp, ref: QueueStreamRef, center: Int): ReadingSession {
        val clip = flow.tts.state.value
        val queue = ref.book
        return ReaderSessions.open(
            bookId = QueueBook.ID,
            sessionId = ReadingSessionId(PositionDomain.Queue, ""),
            book = queue.reader,
            center = center,
            rules = emptyList(),
            targetChars = clip.clipTargetChars,
            flexChars = clip.clipFlexChars,
            contentKey = queue.contentKey(ref.global, ref.groups),
            rulesFor = { ref.book.rulesFor(it, ref.global, ref.groups) },
            current = { ref.book.reader },
            locator = { s -> ProgressLocator { locus, at -> ref.book.locate(s, locus, at) } },
        )
    }
}

/** The Queue stream a session reads; [book] is swapped when rows are appended or marked done. */
internal class QueueStreamRef(
    book: QueueBook,
    val global: List<FilterRule>,
    val groups: List<FilterRule>,
) {
    @Volatile
    var book: QueueBook = book

    val contentKey: Int get() = book.contentKey(global, groups)
}
