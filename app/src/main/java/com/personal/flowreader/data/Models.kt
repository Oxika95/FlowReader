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

    /** Always-present engine choices, ahead of any installed Android TTS engines. */
    val BUILT_IN: List<TtsEngineOption> = listOf(
        TtsEngineOption(EDGE, "Edge TTS"),
        TtsEngineOption(SYSTEM_DEFAULT, "System Default"),
    )
}

data class TtsEngineOption(
    val key: String,
    val label: String,
)

data class TtsVoiceOption(
    val id: String,
    val label: String,
)

enum class BookSource {
    Imported,
    Linked,
    ;

    val label: String
        get() = when (this) {
            Imported -> "Imported"
            Linked -> "Linked"
        }
}

enum class LibraryViewMode {
    List,
    Shelf,
}

/** Selected library tab, including plugin ids. Persisted as [persistKey]. */
sealed class LibraryTabId {
    abstract val persistKey: String

    data object Files : LibraryTabId() {
        override val persistKey: String = ID_FILES
    }

    data object Que : LibraryTabId() {
        override val persistKey: String = ID_QUE
    }

    data class Plugin(val pluginId: String) : LibraryTabId() {
        override val persistKey: String = pluginId
    }

    companion object {
        const val ID_FILES = "files"
        const val ID_QUE = "que"

        fun parse(raw: String?, knownPluginIds: Set<String>): LibraryTabId =
            when (raw) {
                null, ID_FILES, "Files" -> Files
                ID_QUE, "Que" -> Que
                else -> if (raw in knownPluginIds) Plugin(raw) else Files
            }
    }
}

/** Result of sharing text into the library and/or Que. */
data class TextIngestResult(
    val progress: ProgressEntity,
    val queItem: QueItemEntity? = null,
)
