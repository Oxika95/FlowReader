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
    /** Every Queue row in order; rows whose book can't be opened are left out (and logged). */
    suspend fun build(flow: FlowApp): QueueBook = withContext(Dispatchers.IO) {
        val entries = flow.catalog.listQue()
        QueueBook(entries.mapNotNull { segment(flow, it) })
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

    suspend fun open(
        flow: FlowApp,
        queue: QueueBook,
        center: Int,
        global: List<FilterRule>,
        groups: List<FilterRule>,
    ): ReadingSession {
        val clip = flow.tts.state.value
        return ReaderSessions.open(
            bookId = QueueBook.ID,
            book = queue.reader,
            center = center,
            rules = emptyList(),
            targetChars = clip.clipTargetChars,
            flexChars = clip.clipFlexChars,
            contentKey = queue.contentKey(global, groups),
            rulesFor = { queue.rulesFor(it, global, groups) },
        )
    }
}
