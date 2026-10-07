package com.personal.flowreader.data

/**
 * Sentences of a contiguous chapter range. Indices are stable: adding a chapter at either end or
 * trimming chapters never renumbers the rest, so TTS playhead, prefetch jobs and cached clips
 * keyed by index stay valid while the reading window moves. The first sentence of the chapter
 * the table was started from is [ORIGIN]; earlier chapters get smaller indices.
 */
class SentenceTable private constructor(
    val firstIndex: Int,
    private val items: List<Sentence>,
    /** Loaded chapters, contiguous; null when empty. */
    val chapters: IntRange?,
) {
    val size: Int get() = items.size
    val lastIndex: Int get() = firstIndex + items.size - 1
    val indices: IntRange get() = firstIndex..lastIndex

    fun isEmpty(): Boolean = items.isEmpty()
    fun isNotEmpty(): Boolean = items.isNotEmpty()

    fun getOrNull(index: Int): Sentence? = items.getOrNull(index - firstIndex)

    private val blockRanges: Map<Long, IntRange> by lazy {
        val out = HashMap<Long, IntRange>()
        var i = 0
        while (i < items.size) {
            val s = items[i]
            var j = i
            while (j + 1 < items.size && items[j + 1].chapterIndex == s.chapterIndex &&
                items[j + 1].blockIndex == s.blockIndex
            ) {
                j++
            }
            out[key(s.chapterIndex, s.blockIndex)] = (firstIndex + i)..(firstIndex + j)
            i = j + 1
        }
        out
    }

    /** Indices of the sentences in one block (empty when the block has none or isn't loaded). */
    fun inBlock(chapterIndex: Int, blockIndex: Int): IntRange =
        blockRanges[key(chapterIndex, blockIndex)] ?: IntRange.EMPTY

    /**
     * Sentence containing [locus]; else the block's first sentence; else the nearest loaded
     * sentence at or after the locus; else [firstIndex].
     */
    fun indexAt(locus: Locus): Int {
        val block = inBlock(locus.chapterIndex, locus.blockIndex)
        if (!block.isEmpty()) {
            for (i in block) {
                val s = items[i - firstIndex]
                if (locus.charOffset in s.start until s.end.coerceAtLeast(s.start + 1)) return i
            }
            return block.first
        }
        val after = items.indexOfFirst {
            it.chapterIndex > locus.chapterIndex ||
                (it.chapterIndex == locus.chapterIndex && it.blockIndex >= locus.blockIndex)
        }
        return if (after >= 0) firstIndex + after else if (items.isEmpty()) firstIndex else lastIndex
    }

    fun containsChapter(chapterIndex: Int): Boolean = chapters?.contains(chapterIndex) == true

    /** Add [chapterIndex] directly after or before the loaded range. */
    fun withChapter(chapterIndex: Int, sentences: List<Sentence>): SentenceTable {
        val range = chapters ?: return SentenceTable(ORIGIN, sentences, chapterIndex..chapterIndex)
        return when (chapterIndex) {
            range.last + 1 -> SentenceTable(firstIndex, items + sentences, range.first..chapterIndex)
            range.first - 1 -> SentenceTable(firstIndex - sentences.size, sentences + items, chapterIndex..range.last)
            else -> throw IllegalArgumentException("Chapter $chapterIndex is not adjacent to $range")
        }
    }

    /** Drop chapters outside [keep] (clamped to the loaded range). */
    fun retain(keep: IntRange): SentenceTable {
        val range = chapters ?: return this
        val lo = maxOf(range.first, keep.first)
        val hi = minOf(range.last, keep.last)
        if (lo == range.first && hi == range.last) return this
        if (lo > hi) return EMPTY
        val dropFront = items.indexOfFirst { it.chapterIndex >= lo }.let { if (it < 0) items.size else it }
        val keepUntil = items.indexOfLast { it.chapterIndex <= hi } + 1
        return SentenceTable(firstIndex + dropFront, items.subList(dropFront, maxOf(dropFront, keepUntil)).toList(), lo..hi)
    }

    companion object {
        /** Leaves room for chapters loaded before the starting one without going negative. */
        const val ORIGIN = 1_000_000

        val EMPTY = SentenceTable(ORIGIN, emptyList(), null)

        /** [byChapter] must be contiguous; [origin]'s first sentence gets [ORIGIN]. */
        fun of(byChapter: Map<Int, List<Sentence>>, origin: Int): SentenceTable {
            if (byChapter.isEmpty()) return EMPTY
            val keys = byChapter.keys.sorted()
            require(keys.last() - keys.first() + 1 == keys.size) { "Chapters must be contiguous: $keys" }
            val before = keys.filter { it < origin }.sumOf { byChapter.getValue(it).size }
            return SentenceTable(ORIGIN - before, keys.flatMap { byChapter.getValue(it) }, keys.first()..keys.last())
        }

        private fun key(chapterIndex: Int, blockIndex: Int): Long =
            (chapterIndex.toLong() shl 32) or (blockIndex.toLong() and 0xffffffffL)
    }
}
