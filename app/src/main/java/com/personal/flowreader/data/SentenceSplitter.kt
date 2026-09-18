package com.personal.flowreader.data

object SentenceSplitter {
    private val boundary = Regex("(?<=[.!?])\\s+")

    fun split(doc: BookDoc): List<Sentence> {
        val out = ArrayList<Sentence>()
        doc.chapters.forEachIndexed { ci, chapter ->
            chapter.blocks.forEachIndexed { bi, block ->
                val text = block.text.trim()
                if (text.isEmpty()) return@forEachIndexed
                val parts = text.split(boundary).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size <= 1) {
                    out += Sentence(ci, bi, 0, text.length, text)
                    return@forEachIndexed
                }
                var cursor = 0
                for (part in parts) {
                    val idx = text.indexOf(part, cursor)
                    val start = if (idx >= 0) idx else cursor
                    val end = (start + part.length).coerceAtMost(text.length)
                    out += Sentence(ci, bi, start, end, part)
                    cursor = end
                }
            }
        }
        return out
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
