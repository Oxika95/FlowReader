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

enum class ThemeMode {
    Light,
    Dark,
    Oled,
    ;

    val label: String
        get() = when (this) {
            Light -> "Light"
            Dark -> "Dark"
            Oled -> "OLED"
        }
}

/**
 * Default accent hue (purple). Saturation/lightness are resolved per [ThemeMode]
 * so accents stay readable on light, dark, and OLED backgrounds.
 */
object AccentHue {
    const val DEFAULT = 288f
    const val MIN = 0f
    const val MAX = 360f
}

enum class ReaderFont {
    Sans,
    Serif,
    Mono,
    ;

    val label: String
        get() = when (this) {
            Sans -> "Sans"
            Serif -> "Serif"
            Mono -> "Mono"
        }
}

enum class ReaderOrientation {
    Auto,
    Portrait,
    Landscape,
    ;

    val label: String
        get() = when (this) {
            Auto -> "Auto"
            Portrait -> "Portrait"
            Landscape -> "Landscape"
        }
}

/** Virtual Edge engine, or an Android TTS engine package / system default. */
object TtsEngines {
    const val EDGE = "edge"
    const val SYSTEM_DEFAULT = "system"
}

data class TtsEngineOption(
    val key: String,
    val label: String,
)

data class TtsVoiceOption(
    val id: String,
    val label: String,
)
