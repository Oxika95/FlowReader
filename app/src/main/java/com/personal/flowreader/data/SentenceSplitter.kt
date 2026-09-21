package com.personal.flowreader.data

object SentenceSplitter {
    private val boundary = Regex("(?<=[.!?])\\s+")
    /** Soft cap so Edge TTS never receives a multi-thousand-character "sentence". */
    const val MAX_SENTENCE_CHARS = 1_500

    fun split(doc: BookDoc): List<Sentence> {
        val out = ArrayList<Sentence>()
        doc.chapters.forEachIndexed { ci, chapter ->
            chapter.blocks.forEachIndexed { bi, block ->
                val text = block.text.trim()
                if (text.isEmpty()) return@forEachIndexed
                val parts = text.split(boundary).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size <= 1) {
                    appendCapped(out, ci, bi, text, 0)
                    return@forEachIndexed
                }
                var cursor = 0
                for (part in parts) {
                    val idx = text.indexOf(part, cursor)
                    val start = if (idx >= 0) idx else cursor
                    appendCapped(out, ci, bi, part, start)
                    cursor = start + part.length
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
