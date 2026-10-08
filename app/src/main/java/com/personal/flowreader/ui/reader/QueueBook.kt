package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.BookMeter
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.LocusAnchor
import com.personal.flowreader.data.ProgressLocator
import com.personal.flowreader.data.ProgressUpdate
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.data.TextFilters

/** One Queue row inside a [QueueBook]: its own book, Local filters and saved position. */
internal class QueueSegment(
    val queId: String,
    val bookId: String,
    val title: String,
    val done: Boolean,
    val storedPath: String,
    val book: ReaderBook,
    val local: List<FilterRule>,
)

/**
 * The Queue read as one document: [segments] in Queue order, each contributing its chapters.
 * Global chapter = segment base + the item's own chapter index. Block ids get a per-segment prefix
 * (two TXT items both start with `t0-0`). Positions are stored per item through [locator].
 */
internal class QueueBook(val segments: List<QueueSegment>) {
    /** First global chapter of each segment. */
    private val bases: IntArray = IntArray(segments.size).also { out ->
        var next = 0
        segments.forEachIndexed { i, s ->
            out[i] = next
            next += s.book.chapterCount
        }
    }

    val chapterCount: Int = segments.sumOf { it.book.chapterCount }

    /** Queue order identity; a reordered, added or removed row changes it. */
    val signature: String = segments.joinToString("|") { it.queId }

    private val toc: List<Pair<Int, String>>

    /** Indent level per ToC row: 0 = Queue item, 1 = a chapter inside it. */
    val tocLevels: List<Int>

    /** ToC grouped by Queue item, rows indexing [ReaderBook.toc] of [reader]. */
    val tocItems: List<QueueTocItem>

    init {
        val rows = ArrayList<Pair<Int, String>>()
        val levels = ArrayList<Int>()
        val items = ArrayList<QueueTocItem>()
        segments.forEachIndexed { seg, s ->
            val itemRow = rows.size
            rows += bases[seg] to s.title
            levels += 0
            val chapters = ArrayList<Pair<Int, String>>()
            if (s.book.toc.size > 1) {
                s.book.toc.forEach { (chapter, label) ->
                    chapters += rows.size to label
                    rows += (bases[seg] + chapter) to label
                    levels += 1
                }
            }
            items += QueueTocItem(s.queId, s.title, s.done, itemRow, chapters)
        }
        toc = rows
        tocLevels = levels
        tocItems = items
    }

    val reader: ReaderBook = ReaderBook(
        title = TITLE,
        chapterCount = chapterCount,
        titles = List(chapterCount) { i ->
            val seg = segmentIndexOf(i)
            chapterTitle(seg, i - bases[seg], segments[seg].book.chapterTitles.getOrElse(i - bases[seg]) { "" })
        },
        toc = toc,
        meter = BookMeter.concat(segments.map { it.book.meter }),
        hrefs = { i ->
            val seg = segmentIndexOf(i)
            "${segments[seg].queId}#${segments[seg].book.href(i - bases[seg])}"
        },
        hrefIndex = { href ->
            val seg = indexOfQue(href.substringBefore('#'))
            if (seg < 0) {
                -1
            } else {
                val local = segments[seg].book.indexOfHref(href.substringAfter('#', ""))
                bases[seg] + local.coerceAtLeast(0)
            }
        },
        load = { i -> loadChapter(i) },
    )

    fun segmentIndexOf(chapter: Int): Int {
        if (segments.isEmpty()) return -1
        var lo = 0
        var hi = segments.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (bases[mid] <= chapter) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun segmentAt(chapter: Int): QueueSegment? = segments.getOrNull(segmentIndexOf(chapter))

    fun indexOfQue(queId: String): Int = segments.indexOfFirst { it.queId == queId }

    fun baseOf(segment: Int): Int = bases[segment]

    /** Segment index and the locus in that item's own chapter indices. */
    fun toLocal(locus: Locus): Pair<Int, Locus> {
        val seg = segmentIndexOf(locus.chapterIndex)
        return seg to locus.copy(chapterIndex = locus.chapterIndex - bases[seg])
    }

    fun toGlobal(segment: Int, local: Locus): Locus {
        val count = segments[segment].book.chapterCount
        return local.copy(chapterIndex = bases[segment] + local.chapterIndex.coerceIn(0, count - 1))
    }

    /**
     * [locus] in [from] mapped into this stream by Queue row. A removed row lands at the start of
     * the next surviving row (else the previous one); null when this stream is empty.
     */
    fun remap(locus: Locus, from: QueueBook): Locus? {
        if (segments.isEmpty()) return null
        if (from.segments.isEmpty()) return Locus(0, 0, 0)
        val (seg, local) = from.toLocal(locus)
        val same = indexOfQue(from.segments[seg].queId)
        if (same >= 0) return toGlobal(same, local)
        val survivor = ((seg + 1 until from.segments.size) + (seg - 1 downTo 0))
            .map { indexOfQue(from.segments[it].queId) }
            .firstOrNull { it >= 0 }
            ?: 0
        return Locus(bases[survivor], 0, 0)
    }

    /** Global + Groups + the item's own Local rules for [chapter]. */
    fun rulesFor(chapter: Int, global: List<FilterRule>, groups: List<FilterRule>): List<FilterRule> =
        TextFilters.merge(global, groups, segmentAt(chapter)?.local.orEmpty())

    /** Same order and same visual filters ⇒ same key; a session with it can be reused. */
    fun contentKey(global: List<FilterRule>, groups: List<FilterRule>): Int =
        (signature to segments.map { s -> ReaderSessions.contentKey(TextFilters.merge(global, groups, s.local)) })
            .hashCode()

    fun withLocal(segment: Int, local: List<FilterRule>): QueueBook = QueueBook(
        segments.mapIndexed { i, s ->
            if (i != segment) s else QueueSegment(s.queId, s.bookId, s.title, s.done, s.storedPath, s.book, local)
        },
    )

    /** Writes go to the item under the locus, in its own indices, with its own progress fraction. */
    fun locator(session: ReadingSession) = ProgressLocator { locus, at ->
        val (seg, local) = toLocal(locus)
        val s = segments[seg]
        val prepared = session.window.value.chapters[locus.chapterIndex]
        val blocks = prepared?.chapter?.blocks?.size ?: 0
        val within = if (blocks > 0) locus.blockIndex.toFloat() / blocks else 0f
        ProgressUpdate(
            bookId = s.bookId,
            chapterIndex = local.chapterIndex,
            blockIndex = local.blockIndex,
            charOffset = local.charOffset,
            fraction = s.book.meter.fraction(local.chapterIndex, within),
            at = at,
            anchorText = prepared?.let { LocusAnchor.of(it.chapter, locus) },
            chapterHref = s.book.href(local.chapterIndex),
        )
    }

    private suspend fun loadChapter(index: Int): Chapter {
        val seg = segmentIndexOf(index)
        val local = index - bases[seg]
        val raw = segments[seg].book.load(local)
        return Chapter(
            title = chapterTitle(seg, local, raw.title),
            blocks = raw.blocks.map { it.copy(id = "q$seg-${it.id}") },
        )
    }

    /** An item's first chapter carries the item title, so it heads each document in the stream. */
    private fun chapterTitle(segment: Int, local: Int, own: String): String =
        if (local == 0) segments[segment].title else own

    companion object {
        /** Session and locator id of the Queue stream (never a real book id). */
        const val ID = "queue"
        const val TITLE = "Queue"
    }
}

/** One Queue item in the Contents card: its ToC row and its nested chapter rows. */
data class QueueTocItem(
    val queId: String,
    val title: String,
    val done: Boolean,
    val row: Int,
    val chapters: List<Pair<Int, String>>,
)

/** How a rebuilt Queue differs from the open stream. */
internal enum class QueueChange {
    Same,

    /** Same rows in the same order; only Done flags changed. */
    DoneOnly,

    /** The old rows are an ordered prefix: existing chapter indices are unchanged. */
    Appended,

    /** Reordered, removed or inserted rows: chapter indices moved. */
    Edited,
    ;

    companion object {
        fun classify(old: QueueBook, new: QueueBook): QueueChange {
            val oldIds = old.segments.map { it.queId }
            val newIds = new.segments.map { it.queId }
            val prefix = newIds.size >= oldIds.size && newIds.subList(0, oldIds.size) == oldIds &&
                old.segments.indices.all { old.segments[it].book.chapterCount == new.segments[it].book.chapterCount }
            return when {
                !prefix -> Edited
                newIds.size > oldIds.size -> Appended
                old.segments.map { it.done } == new.segments.map { it.done } -> Same
                else -> DoneOnly
            }
        }
    }
}
