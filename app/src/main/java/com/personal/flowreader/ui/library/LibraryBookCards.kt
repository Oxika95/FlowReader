package com.personal.flowreader.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.plugin.PluginManager
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.design.card.FlowCornerBadge
import com.personal.flowreader.ui.design.card.FlowDisplayCard
import com.personal.flowreader.ui.design.card.FlowDisplayGrid
import com.personal.flowreader.ui.design.card.FlowDisplayLayout
import com.personal.flowreader.ui.design.card.FlowDisplayList
import com.personal.flowreader.ui.design.card.FlowEmptyState
import com.personal.flowreader.ui.design.card.flowDisplayListPadding
import com.personal.flowreader.ui.design.controls.FlowBadgeTone
import com.personal.flowreader.ui.design.controls.newChaptersLabel
import com.personal.flowreader.ui.theme.FlowTokens

private val LinkedCorner = FlowCornerBadge(Icons.Filled.Link, "Linked file")

/** Books as display cards: [LibraryViewMode.List] rows or a [LibraryViewMode.Shelf] tile grid. */
@Composable
fun LibraryBooksPane(
    books: List<BookItem>,
    viewMode: LibraryViewMode,
    busy: Boolean,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = FlowTokens.Space.None,
    /** Author line under the title; null = [BookItem.author] (or source · last read when blank). */
    authorFor: ((BookItem) -> String)? = null,
    /** Chapter total for the progress chip; null = [BookItem.chapterCount]. */
    chapterCountFor: (BookItem) -> Int = { it.chapterCount },
    /**
     * Unread-ahead count for the "N new" chip (live/synced plugin lists). 0 hides the chip.
     * Files / local shelves leave the default.
     */
    unreadNewFor: (BookItem) -> Int = { 0 },
    /** Double-tap opens the reader; null = single tap only (media card / splash). */
    onDoubleOpen: ((String) -> Unit)? = null,
    /** Extra badges after the chapter / new chips (e.g. position conflict). */
    badgesFor: (BookItem) -> List<Pair<String, FlowBadgeTone>> = { emptyList() },
    /** True when this book still has a chapter-update notification in the system shade. */
    notifyDotFor: (BookItem) -> Boolean = { false },
) {
    Box(modifier.fillMaxSize()) {
        if (books.isEmpty() && !busy) {
            FlowEmptyState(emptyMessage)
            return@Box
        }
        val padding: PaddingValues = flowDisplayListPadding(bottomExtra = bottomInset)
        when (viewMode) {
            LibraryViewMode.List -> FlowDisplayList(contentPadding = padding) {
                items(books, key = { it.bookId }) { book ->
                    BookCard(
                        book = book,
                        layout = FlowDisplayLayout.Row,
                        author = authorFor?.invoke(book) ?: filesAuthorLine(book),
                        chapterCount = chapterCountFor(book),
                        unreadNew = unreadNewFor(book),
                        onOpen = onOpen,
                        onDoubleOpen = onDoubleOpen,
                        extraBadges = badgesFor(book),
                        notifyDot = notifyDotFor(book),
                    )
                }
            }
            LibraryViewMode.Shelf -> FlowDisplayGrid(contentPadding = padding) {
                items(books, key = { it.bookId }) { book ->
                    BookCard(
                        book = book,
                        layout = FlowDisplayLayout.Tile,
                        author = authorFor?.invoke(book) ?: filesAuthorLine(book),
                        chapterCount = chapterCountFor(book),
                        unreadNew = unreadNewFor(book),
                        onOpen = onOpen,
                        onDoubleOpen = onDoubleOpen,
                        extraBadges = badgesFor(book),
                        notifyDot = notifyDotFor(book),
                    )
                }
            }
        }
    }
}

private fun filesAuthorLine(book: BookItem): String =
    book.author.ifBlank { libraryBookSubtitle(book) }

@Composable
private fun BookCard(
    book: BookItem,
    layout: FlowDisplayLayout,
    author: String,
    chapterCount: Int,
    unreadNew: Int,
    onOpen: (String) -> Unit,
    onDoubleOpen: ((String) -> Unit)?,
    extraBadges: List<Pair<String, FlowBadgeTone>>,
    notifyDot: Boolean,
) {
    val edge = if (layout == FlowDisplayLayout.Row) FlowTokens.CoverEdge.Row else FlowTokens.CoverEdge.Tile
    val cover by rememberBookCover(book, maxEdge = edge)
    val badges = buildList {
        LibraryRowText.chapterChip(chapterCount, book.chapterIndex)?.let { chip ->
            add(chip.label to chip.kind.toTone())
        }
        val newLabel = newChaptersLabel(unreadNew)
        if (newLabel.isNotEmpty()) add(newLabel to FlowBadgeTone.Accent)
        addAll(extraBadges)
    }
    FlowDisplayCard(
        title = book.title,
        art = cover,
        onClick = { onOpen(book.bookId) },
        layout = layout,
        subtitle = author,
        badges = badges.map { it.first },
        badgeTones = badges.toMap(),
        progress = book.readingProgress,
        corner = LinkedCorner.takeIf { book.sourceKind == BookSource.Linked.name },
        onDoubleClick = onDoubleOpen?.let { handler -> { handler(book.bookId) } },
        notifyDot = notifyDot,
    )
}

private fun ChapterChipKind.toTone(): FlowBadgeTone = when (this) {
    ChapterChipKind.Unstarted -> FlowBadgeTone.Accent
    ChapterChipKind.InProgress -> FlowBadgeTone.Neutral
    ChapterChipKind.Complete -> FlowBadgeTone.Muted
}

internal fun librarySourceLabel(book: BookItem): String =
    PluginManager.displayNames[book.sourceKind]
        ?: runCatching { BookSource.valueOf(book.sourceKind).label }
            .getOrDefault(BookSource.Imported.label)

internal fun libraryLastRead(book: BookItem): String =
    DateUtils.getRelativeTimeSpanString(
        book.lastReadAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

internal fun libraryBookSubtitle(book: BookItem): String =
    "${librarySourceLabel(book)} · ${libraryLastRead(book)}"
