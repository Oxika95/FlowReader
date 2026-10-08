package com.personal.flowreader.ui.reader

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.QueEntry
import com.personal.flowreader.data.ReadingSession
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
        val entries = flow.catalog.listQue()
        val known = reuse?.segments?.associateBy { it.queId }.orEmpty()
        QueueBook(
            entries.mapNotNull { entry ->
                val old = known[entry.item.id]?.takeIf { it.bookId == entry.progress.bookId }
                if (old != null) {
                    QueueSegment(old.queId, old.bookId, entry.progress.title, entry.item.done, old.storedPath, old.book, old.local)
                } else {
                    segment(flow, entry)
                }
            },
        )
    }

    private suspend fun segment(flow: FlowApp, entry: QueEntry): QueueSegment? {
        val row = entry.progress
        val book = try {
            if (flow.pluginBooks.isPluginBook(row.bookId)) {
                ReaderBook.plugin(flow.pluginBooks, flow.pluginBooks.openStory(row.bookId))
            } else {
                ReaderBook.local(ChapterSource.open(flow.catalog.materialize(row), row.title))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            SynthDebugLog.appendError("Queue: skipped \"${row.title}\" (${t.message ?: t.javaClass.simpleName})")
            return null
        }
        if (book.chapterCount == 0) return null
        return QueueSegment(
            queId = entry.item.id,
            bookId = row.bookId,
            title = row.title,
            done = entry.item.done,
            storedPath = row.storedPath,
            book = book,
            local = TextFilters.decodeRules(flow.db.bookFilters().get(row.bookId)?.rulesJson),
        )
    }

    /** Session over [ref]: loads read [QueueStreamRef.book], so appended rows show up in place. */
    suspend fun open(flow: FlowApp, ref: QueueStreamRef, center: Int): ReadingSession {
        val clip = flow.tts.state.value
        val queue = ref.book
        return ReaderSessions.open(
            bookId = QueueBook.ID,
            book = queue.reader,
            center = center,
            rules = emptyList(),
            targetChars = clip.clipTargetChars,
            flexChars = clip.clipFlexChars,
            contentKey = queue.contentKey(ref.global, ref.groups),
            rulesFor = { ref.book.rulesFor(it, ref.global, ref.groups) },
            current = { ref.book.reader },
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
