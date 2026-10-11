package com.personal.flowreader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.data.QueueItemEntity
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One chapter under an expanded Queue row; [chapter] is the item's own chapter index. */
data class QueueChapterRow(val chapter: Int, val label: String, val done: Boolean)

internal object QueueChapters {
    /**
     * Chapter rows for a Queue item, in the same shape as the reader's Contents card: nothing for
     * single-chapter items. Chapters before [savedChapter] (or all, when the item is [itemDone])
     * are done.
     */
    fun rows(toc: List<Pair<Int, String>>, savedChapter: Int, itemDone: Boolean): List<QueueChapterRow> {
        if (toc.size <= 1) return emptyList()
        return toc.mapIndexed { i, (chapter, label) ->
            QueueChapterRow(chapter, label.ifBlank { "Chapter ${i + 1}" }, itemDone || chapter < savedChapter)
        }
    }

    /** The item's ToC from local metadata only (no network); empty when it can't be read. */
    suspend fun toc(flow: FlowApp, item: QueueItemEntity): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        try {
            if (flow.pluginBooks.isPluginBook(item.bookId)) {
                flow.pluginBooks.session(item.bookId)?.toc.orEmpty().mapIndexed { i, ref -> i to ref.title }
            } else {
                ChapterSource.open(flow.catalog.materialize(item), item.title).tocEntries()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

/** Chapters under an expanded Queue row; done chapters are muted. Tap opens the Queue there. */
@Composable
internal fun QueueChapterList(rows: List<QueueChapterRow>, onChapter: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Text(
                row.label,
                style = FlowType.label,
                color = if (row.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (row.done) FlowTokens.Alpha.Disabled else 1f)
                    .semantics { if (row.done) stateDescription = "Read" }
                    .clickable { onChapter(row.chapter) }
                    .padding(start = FlowTokens.Space.XL, top = FlowTokens.Space.S, bottom = FlowTokens.Space.S),
            )
        }
    }
}

/** Queue id → chapter rows for every entry; ToCs are read once per entry and book. */
@Composable
internal fun rememberQueueChapters(entries: List<QueueItemEntity>): Map<String, List<QueueChapterRow>> {
    val flow = LocalContext.current.applicationContext as FlowApp
    val tocs = remember { mutableStateMapOf<String, List<Pair<Int, String>>>() }
    LaunchedEffect(entries.map { it.queId to it.bookId }) {
        entries.forEach { item ->
            val key = "${item.queId}|${item.bookId}"
            if (key !in tocs) tocs[key] = QueueChapters.toc(flow, item)
        }
    }
    return entries.associate { item ->
        val toc = tocs["${item.queId}|${item.bookId}"].orEmpty()
        item.queId to QueueChapters.rows(toc, item.position.chapterIndex, item.done)
    }
}
