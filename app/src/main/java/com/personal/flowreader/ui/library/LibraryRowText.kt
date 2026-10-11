package com.personal.flowreader.ui.library

/** Library list row subtitles for plugin stories and Files books. */
object LibraryRowText {
    /**
     * "{author} · chapter {x}/{n}" when [savedChapter] (0-based, -1 = unread) is set, else
     * "{author} · {n} chapters"; null when neither an author nor a chapter count is known.
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
}
