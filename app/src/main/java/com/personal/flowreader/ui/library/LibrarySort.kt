package com.personal.flowreader.ui.library

import com.personal.flowreader.data.BookItem

/** How Files, custom shelves, and plugin story lists order their cards. Queue is manual. */
enum class LibrarySortMode(val label: String) {
    /** Plugin default: unread-ahead first, completed last; title A–Z within each band. */
    Updates("Updates"),
    Title("Title"),
    Author("Author"),
    /** Newest [BookItem.addedAt] first. */
    DateAdded("Date added"),
}

/** Pure list ordering for library cards. */
object LibrarySort {
    val filesModes: List<LibrarySortMode> = listOf(
        LibrarySortMode.DateAdded,
        LibrarySortMode.Title,
        LibrarySortMode.Author,
    )

    val pluginModes: List<LibrarySortMode> = listOf(
        LibrarySortMode.Updates,
        LibrarySortMode.Title,
        LibrarySortMode.Author,
        LibrarySortMode.DateAdded,
    )

    fun defaultFor(plugin: Boolean): LibrarySortMode =
        if (plugin) LibrarySortMode.Updates else LibrarySortMode.DateAdded

    fun parse(raw: String?, allowed: List<LibrarySortMode>, fallback: LibrarySortMode): LibrarySortMode =
        allowed.firstOrNull { it.name == raw } ?: fallback

    /**
     * Sort [books] by [mode]. [chapterCountOf] / [authorOf] let plugin tabs supply list/session meta
     * when [BookItem] fields are empty.
     */
    fun sort(
        books: List<BookItem>,
        mode: LibrarySortMode,
        chapterCountOf: (BookItem) -> Int = { it.chapterCount },
        authorOf: (BookItem) -> String = { it.author },
    ): List<BookItem> = when (mode) {
        LibrarySortMode.Title -> books.sortedWith(compareBy(TITLE))
        LibrarySortMode.Author -> books.sortedWith(
            compareBy<BookItem> { authorOf(it).ifBlank { "\uFFFF" }.lowercase() }
                .thenBy(TITLE),
        )
        LibrarySortMode.DateAdded -> books.sortedWith(
            compareByDescending<BookItem> { it.addedAt }.thenBy(TITLE),
        )
        LibrarySortMode.Updates -> books.sortedWith(
            compareBy<BookItem> { updatesBand(chapterCountOf(it), it.chapterIndex) }
                .thenBy(TITLE),
        )
    }

    /**
     * 0 = chapters after the saved position (same as the "N new" chip),
     * 1 = unstarted / unknown,
     * 2 = complete (n/n).
     */
    fun updatesBand(chapterCount: Int, savedChapter: Int): Int {
        if (LibraryRowText.unreadNew(chapterCount, savedChapter) > 0) return 0
        val chip = LibraryRowText.chapterChip(chapterCount, savedChapter)
        return if (chip?.kind == ChapterChipKind.Complete) 2 else 1
    }

    private val TITLE = { book: BookItem -> book.title.lowercase() }
}
