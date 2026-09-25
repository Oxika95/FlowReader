package com.personal.flowreader.data

/**
 * Splits book text into TTS/reader sentence segments:
 * 1) English Koehn/Schroeder heuristic (abbrev-aware)
 * 2) Length normalize into target±flex for Edge latency
 * 3) Hard char cap so Edge never sees multi-thousand-char utterances
 */
object SentenceSplitter {
    /** Absolute safety cap (Edge max is 4000). */
    const val MAX_SENTENCE_CHARS = 1_500

    const val DEFAULT_TARGET_CHARS = SentenceLengthNormalizer.DEFAULT_TARGET_CHARS
    const val DEFAULT_FLEX_CHARS = SentenceLengthNormalizer.DEFAULT_FLEX_CHARS
    const val MIN_CHARS = SentenceLengthNormalizer.DEFAULT_TARGET_CHARS -
        SentenceLengthNormalizer.DEFAULT_FLEX_CHARS
    const val MAX_CHARS = SentenceLengthNormalizer.DEFAULT_TARGET_CHARS +
        SentenceLengthNormalizer.DEFAULT_FLEX_CHARS

    fun split(
        doc: BookDoc,
        targetChars: Int = DEFAULT_TARGET_CHARS,
        flexChars: Int = DEFAULT_FLEX_CHARS,
    ): List<Sentence> {
        val out = ArrayList<Sentence>()
        doc.chapters.forEachIndexed { ci, chapter ->
            chapter.blocks.forEachIndexed { bi, block ->
                val text = block.text.trim()
                if (text.isEmpty()) return@forEachIndexed
                val parts = SentenceLengthNormalizer.normalize(
                    KoehnSentenceBreak.split(text),
                    targetChars = targetChars,
                    flexChars = flexChars,
                )
                if (parts.isEmpty()) {
                    appendCapped(out, ci, bi, text, 0)
                    return@forEachIndexed
                }
                var cursor = 0
                for (part in parts) {
                    val idx = text.indexOf(part, cursor)
                    val start = if (idx >= 0) {
                        idx
                    } else {
                        val key = part.take(16)
                        val fallback = text.indexOf(key, cursor)
                        if (fallback >= 0) fallback else cursor
                    }
                    appendCapped(out, ci, bi, part, start)
                    cursor = (start + part.length).coerceAtMost(text.length)
                }
            }
        }
        return out
    }

    private fun appendCapped(
        out: MutableList<Sentence>,
        chapterIndex: Int,
        blockIndex: Int,
        text: String,
        absoluteStart: Int,
    ) {
        if (text.length <= MAX_SENTENCE_CHARS) {
            out += Sentence(chapterIndex, blockIndex, absoluteStart, absoluteStart + text.length, text)
            return
        }
        var offset = 0
        while (offset < text.length) {
            val end = (offset + MAX_SENTENCE_CHARS).coerceAtMost(text.length)
            val chunk = text.substring(offset, end)
            out += Sentence(
                chapterIndex,
                blockIndex,
                absoluteStart + offset,
                absoluteStart + end,
                chunk,
            )
            offset = end
        }
    }

    fun indexAt(sentences: List<Sentence>, locus: Locus): Int {
        val i = sentences.indexOfFirst {
            it.chapterIndex == locus.chapterIndex &&
                it.blockIndex == locus.blockIndex &&
                locus.charOffset in it.start until it.end.coerceAtLeast(it.start + 1)
        }
        if (i >= 0) return i
        return sentences.indexOfFirst {
            it.chapterIndex == locus.chapterIndex && it.blockIndex == locus.blockIndex
        }.coerceAtLeast(0)
    }
}
