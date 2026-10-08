package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.LocusAnchor
import com.personal.flowreader.data.PreparedChapter
import com.personal.flowreader.data.ProgressLocator
import com.personal.flowreader.data.ProgressUpdate
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.data.ReadingWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds [ReadingSession]s over a [ReaderBook]; file and parse work stays off the main thread. */
internal object ReaderSessions {
    /** Cover, title and copyright pages are skipped when a book opens on them. */
    private const val MAX_EMPTY_SKIP = 8

    /** Identity of the displayed text: visual filters (speech-only rules don't change it). */
    fun contentKey(rules: List<FilterRule>): Int = rules.filterNot { it.ttsOnly }.hashCode()

    suspend fun prepare(
        book: ReaderBook,
        index: Int,
        rules: List<FilterRule>,
        targetChars: Int,
        flexChars: Int,
    ): PreparedChapter {
        val raw = withContext(Dispatchers.IO) {
            try {
                book.load(index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                unreadable(book, index, e)
            }
        }
        return withContext(Dispatchers.Default) {
            PreparedChapter.of(index, raw, rules, targetChars, flexChars)
        }
    }

    /**
     * Session holding [center] (and following chapters while it has nothing to read). [rulesFor]
     * gives each chapter its filters (a Queue stream mixes books with different Local rules).
     * [current] is read on every load, so a book that only gains chapters can be swapped in.
     */
    suspend fun open(
        bookId: String,
        book: ReaderBook,
        center: Int,
        rules: List<FilterRule>,
        targetChars: Int,
        flexChars: Int,
        contentKey: Int = contentKey(rules),
        rulesFor: (Int) -> List<FilterRule> = { rules },
        current: () -> ReaderBook = { book },
    ): ReadingSession {
        val first = prepare(book, center, rulesFor(center), targetChars, flexChars)
        val session = ReadingSession(
            bookId = bookId,
            contentKey = contentKey,
            initial = ReadingWindow.of(book.title, book.chapterTitles, listOf(first), origin = center),
            chapterCount = { current().chapterCount },
            loader = { i, t, f -> prepare(current(), i, rulesFor(i), t, f) },
        )
        var next = center + 1
        while (session.window.value.table.isEmpty() && next < book.chapterCount && next - center <= MAX_EMPTY_SKIP) {
            session.loadAdjacent(next++, targetChars, flexChars)
        }
        return session
    }

    /** [locus], or the start of the next loaded chapter with text when its chapter is empty. */
    fun readable(window: ReadingWindow, locus: Locus): Locus {
        if (window.chapters[locus.chapterIndex]?.chapter?.blocks?.isNotEmpty() == true) return locus
        val next = window.chapters.values
            .filter { it.index > locus.chapterIndex && it.chapter.blocks.isNotEmpty() }
            .minByOrNull { it.index }
            ?: return locus
        return Locus(next.index, 0, 0)
    }

    fun fraction(book: ReaderBook, window: ReadingWindow, locus: Locus): Float {
        val blocks = window.chapters[locus.chapterIndex]?.chapter?.blocks?.size ?: 0
        val within = if (blocks > 0) locus.blockIndex.toFloat() / blocks else 0f
        return book.meter.fraction(locus.chapterIndex, within)
    }

    fun locator(bookId: String, book: ReaderBook, session: ReadingSession) = ProgressLocator { locus, at ->
        val window = session.window.value
        ProgressUpdate(
            bookId = bookId,
            chapterIndex = locus.chapterIndex,
            blockIndex = locus.blockIndex,
            charOffset = locus.charOffset,
            fraction = fraction(book, window, locus),
            at = at,
            anchorText = window.chapters[locus.chapterIndex]?.let { LocusAnchor.of(it.chapter, locus) },
            chapterHref = book.href(locus.chapterIndex),
        )
    }

    private fun unreadable(book: ReaderBook, index: Int, error: Exception) = Chapter(
        title = book.chapterTitles.getOrElse(index) { "" },
        blocks = listOf(
            Block(
                id = "unreadable-$index",
                kind = BlockKind.Paragraph,
                text = "This chapter could not be loaded (${error.message ?: error.javaClass.simpleName}).",
            ),
        ),
    )
}
