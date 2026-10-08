package com.personal.flowreader.data

import java.io.File
import java.io.RandomAccessFile

/**
 * Plain text as virtual chapters: byte ranges of about [SEGMENT_BYTES] cut at blank lines, so a
 * chapter never splits a paragraph or a UTF-8 sequence. Opening scans bytes once; [load]
 * reads one range.
 */
class TxtChapterSource private constructor(
    private val file: File,
    override val title: String,
    private val segments: List<LongRange>,
) : ChapterSource {
    override val chapterCount: Int get() = segments.size

    private val titles = HashMap<Int, String>()

    override fun chapterTitle(index: Int): String =
        if (segments.size == 1) title else titles[index] ?: "Part ${index + 1}"

    override fun href(index: Int): String =
        segments.getOrNull(index)?.let { "${it.first}-${it.last + 1}" }.orEmpty()

    override fun indexOfHref(href: String): Int = segments.indices.firstOrNull { href(it) == href } ?: -1

    override fun weight(index: Int): Long = segments.getOrNull(index)?.let { it.last - it.first + 1 } ?: 1L

    override fun tocEntries(): List<Pair<Int, String>> = segments.indices.map { it to chapterTitle(it) }

    override fun load(index: Int): Chapter {
        val range = segments.getOrNull(index) ?: return Chapter(chapterTitle(index), emptyList())
        val bytes = ByteArray((range.last - range.first + 1).toInt())
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(range.first)
            raf.readFully(bytes)
        }
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        val blocks = TxtIngest.paragraphs(text, "t$index-")
        if (segments.size > 1) {
            blocks.firstOrNull()?.text?.takeIf { isHeading(it) }?.let { synchronized(titles) { titles[index] = it } }
        }
        return Chapter(chapterTitle(index), blocks)
    }

    companion object {
        const val SEGMENT_BYTES = 48 * 1024L
        private val HEADING = Regex(
            "^(chapter|part|book|prologue|epilogue|section|act|volume)\\b.{0,60}$",
            RegexOption.IGNORE_CASE,
        )

        private fun isHeading(text: String) = text.length <= 80 && HEADING.matches(text)

        fun open(file: File, title: String): TxtChapterSource =
            TxtChapterSource(file, title, segment(file, SEGMENT_BYTES))

        /** Cut points are paragraph starts (after a blank line) once a segment reaches [target]. */
        internal fun segment(file: File, target: Long): List<LongRange> {
            val length = file.length()
            if (length <= 0L) return emptyList()
            val out = ArrayList<LongRange>()
            var start = 0L
            var pos = 0L
            var newlines = 0
            val buf = ByteArray(64 * 1024)
            file.inputStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    for (k in 0 until n) {
                        when (buf[k].toInt()) {
                            '\n'.code -> newlines++
                            ' '.code, '\t'.code, '\r'.code -> Unit
                            else -> {
                                if (newlines >= 2 && pos - start >= target) {
                                    out += start until pos
                                    start = pos
                                }
                                newlines = 0
                            }
                        }
                        pos++
                    }
                }
            }
            out += start until length
            return out
        }
    }
}
