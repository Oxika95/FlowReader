package com.personal.flowreader.ui.library

/** Progress kind for the library "Chapter n/total" chip. */
enum class ChapterChipKind {
    /** No saved position yet — primary accent. */
    Unstarted,
    /** Reading in progress — secondary. */
    InProgress,
    /** On the last chapter (n/n) — muted grey. */
    Complete,
}

/** Label + kind for a chapter-progress chip. */
data class ChapterChip(val label: String, val kind: ChapterChipKind)

/** Library list row text and chips for plugin stories and Files books. */
object LibraryRowText {
    /**
     * "{author} · chapter {x}/{n}" when [savedChapter] (0-based, -1 = unread) is set, else
     * "{author} · {n} chapters"; null when neither an author nor a chapter count is known.
     * Prefer [chapterChip] + author-only subtitle on library cards.
     */
    fun subtitle(author: String, chapterCount: Int, savedChapter: Int): String? {
        val chapters = when {
            chapterCount <= 0 -> ""
            savedChapter >= 0 -> "chapter ${savedChapter.coerceAtMost(chapterCount - 1) + 1}/$chapterCount"
            chapterCount == 1 -> "1 chapter"
            else -> "$chapterCount chapters"
        }
        return when {
            author.isNotBlank() && chapters.isNotEmpty() -> "$author · $chapters"
            author.isNotBlank() -> author
            chapters.isNotEmpty() -> chapters.replaceFirstChar { it.uppercase() }
            else -> null
        }
    }

    /**
     * "Chapter n/total" chip: 0/total when unstarted, n/total while reading, total/total when done.
     * Null when [chapterCount] is unknown.
     */
    fun chapterChip(chapterCount: Int, savedChapter: Int): ChapterChip? {
        if (chapterCount <= 0) return null
        if (savedChapter < 0) return ChapterChip("Chapter 0/$chapterCount", ChapterChipKind.Unstarted)
        val at = savedChapter.coerceAtMost(chapterCount - 1) + 1
        val kind = if (at >= chapterCount) ChapterChipKind.Complete else ChapterChipKind.InProgress
        return ChapterChip("Chapter $at/$chapterCount", kind)
    }

    /**
     * Chapters after the saved position ("71/72" → 1). 0 when unstarted, caught up, or unknown count.
     * Drives the plugin "N new" chip (live/synced lists only).
     */
    fun unreadNew(chapterCount: Int, savedChapter: Int): Int {
        if (chapterCount <= 0 || savedChapter < 0) return 0
        val at = savedChapter.coerceAtMost(chapterCount - 1)
        return (chapterCount - at - 1).coerceAtLeast(0)
    }
}
