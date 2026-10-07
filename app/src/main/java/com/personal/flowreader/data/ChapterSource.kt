package com.personal.flowreader.data

import java.io.File

/**
 * Random access to one book's chapters without holding the book in memory. Opening reads only
 * metadata (EPUB: container, OPF, nav; TXT: one byte scan for segment bounds); [load] parses a
 * single chapter. Chapter indices are stable for a given file and keyed by [href].
 */
interface ChapterSource {
    val title: String
    val chapterCount: Int

    /** Best label known without parsing (nav/NCX label, TXT heading); [load] may refine it. */
    fun chapterTitle(index: Int): String

    /** Stable key for [index] (EPUB entry path, TXT byte range). */
    fun href(index: Int): String

    fun indexOfHref(href: String): Int

    /** Relative size of [index] for whole-book progress (bytes). */
    fun weight(index: Int): Long

    /** Table of contents rows: chapter index → label, in reading order. */
    fun tocEntries(): List<Pair<Int, String>>

    fun load(index: Int): Chapter

    /**
     * Map a position saved before chapter sources existed (whole-book parse: EPUB chapters
     * counted only when non-empty, TXT as one chapter) onto this source's indices.
     */
    fun legacyLocus(locus: Locus): Locus

    companion object {
        fun open(file: File, fallbackTitle: String = file.nameWithoutExtension): ChapterSource =
            if (file.extension.equals("txt", ignoreCase = true)) {
                TxtChapterSource.open(file, fallbackTitle)
            } else {
                EpubChapterSource.open(file)
            }
    }
}

/** Whole-book progress from per-chapter weights (no chapter text needed). */
class BookMeter(private val weights: LongArray) {
    private val prefix = LongArray(weights.size + 1).also { p ->
        for (i in weights.indices) p[i + 1] = p[i] + weights[i].coerceAtLeast(1L)
    }

    /** [withinChapter] is the 0–1 position inside [chapterIndex]. */
    fun fraction(chapterIndex: Int, withinChapter: Float): Float {
        if (weights.isEmpty()) return 0f
        val ci = chapterIndex.coerceIn(0, weights.lastIndex)
        val total = prefix.last().toFloat()
        val chapter = (prefix[ci + 1] - prefix[ci]).toFloat()
        return ((prefix[ci] + chapter * withinChapter.coerceIn(0f, 1f)) / total).coerceIn(0f, 1f)
    }

    companion object {
        fun of(source: ChapterSource) = BookMeter(LongArray(source.chapterCount) { source.weight(it) })
    }
}
