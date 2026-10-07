package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.BookMeter
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.data.Locus
import com.personal.flowreader.plugin.store.PluginBookStore
import com.personal.flowreader.plugin.store.PluginReadSession

/**
 * What the reader needs from a book, local file or plugin story, without its text: chapters
 * are loaded one at a time through [load]. Chapter indices are absolute (plugin: ToC index).
 */
internal class ReaderBook(
    val title: String,
    val chapterCount: Int,
    private val titles: List<String>,
    /** ToC rows in reading order: chapter index → label. */
    val toc: List<Pair<Int, String>>,
    val meter: BookMeter,
    private val hrefs: (Int) -> String,
    private val hrefIndex: (String) -> Int,
    /** Maps a position saved by the whole-book parser (locusVersion 0). */
    val legacyLocus: (Locus) -> Locus,
    val load: suspend (Int) -> Chapter,
) {
    val chapterTitles: List<String> get() = titles

    fun href(index: Int): String = if (index in 0 until chapterCount) hrefs(index) else ""

    fun indexOfHref(href: String): Int = if (href.isBlank()) -1 else hrefIndex(href)

    /** ToC row highlighted while reading [chapterIndex]. */
    fun tocRowOf(chapterIndex: Int): Int {
        if (toc.isEmpty()) return 0
        val row = toc.indexOfLast { it.first <= chapterIndex }
        return row.coerceAtLeast(0)
    }

    companion object {
        fun local(source: ChapterSource) = ReaderBook(
            title = source.title,
            chapterCount = source.chapterCount,
            titles = List(source.chapterCount) { source.chapterTitle(it) },
            toc = source.tocEntries(),
            meter = BookMeter.of(source),
            hrefs = source::href,
            hrefIndex = source::indexOfHref,
            legacyLocus = source::legacyLocus,
            load = { source.load(it) },
        )

        fun plugin(store: PluginBookStore, story: PluginReadSession): ReaderBook {
            val titles = story.toc.mapIndexed { i, ref -> ref.title.ifBlank { "Chapter ${i + 1}" } }
            return ReaderBook(
                title = story.title,
                chapterCount = story.toc.size,
                titles = titles,
                toc = titles.mapIndexed { i, t -> i to t },
                meter = BookMeter(LongArray(story.toc.size) { 1L }),
                hrefs = { story.toc[it].url },
                hrefIndex = { href -> story.toc.indexOfFirst { it.url == href } },
                legacyLocus = { it },
                load = { store.chapter(story, it) },
            )
        }
    }
}
