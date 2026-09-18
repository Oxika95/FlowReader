package com.personal.flowreader.data

enum class BlockKind {
    Heading,
    Paragraph,
    Quote,
}

data class Block(
    val id: String,
    val kind: BlockKind,
    val text: String,
)

data class Chapter(
    val title: String,
    val blocks: List<Block>,
)

data class BookDoc(
    val title: String,
    val chapters: List<Chapter>,
) {
    val items: List<ReaderItem> by lazy {
        chapters.flatMapIndexed { chapterIndex, chapter ->
            chapter.blocks.mapIndexed { blockIndex, block ->
                ReaderItem(chapterIndex, blockIndex, block)
            }
        }
    }
}

data class ReaderItem(
    val chapterIndex: Int,
    val blockIndex: Int,
    val block: Block,
)

data class Locus(
    val chapterIndex: Int = 0,
    val blockIndex: Int = 0,
    val charOffset: Int = 0,
) {
    fun flatIndex(doc: BookDoc): Int {
        var i = 0
        doc.chapters.forEachIndexed { ci, ch ->
            if (ci < chapterIndex) {
                i += ch.blocks.size
            } else if (ci == chapterIndex) {
                i += blockIndex.coerceAtMost(ch.blocks.lastIndex.coerceAtLeast(0))
                return i
            }
        }
        return (i - 1).coerceAtLeast(0)
    }
}

data class Sentence(
    val chapterIndex: Int,
    val blockIndex: Int,
    val start: Int,
    val end: Int,
    val text: String,
)

enum class ThemeMode { Light, Dark, Oled }

enum class TtsEngineKind { System, Edge }
