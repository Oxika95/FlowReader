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
    subtitleFor: (BookItem) -> String = { libraryBookSubtitle(it) },
    onLongOpen: ((String) -> Unit)? = null,
    badgesFor: (BookItem) -> List<Pair<String, FlowBadgeTone>> = { emptyList() },
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
                    BookCard(book, FlowDisplayLayout.Row, subtitleFor(book), onOpen, onLongOpen, badgesFor(book))
                }
            }
            LibraryViewMode.Shelf -> FlowDisplayGrid(contentPadding = padding) {
                items(books, key = { it.bookId }) { book ->
                    BookCard(book, FlowDisplayLayout.Tile, "", onOpen, onLongOpen, badgesFor(book))
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    book: BookItem,
    layout: FlowDisplayLayout,
    subtitle: String,
    onOpen: (String) -> Unit,
    onLongOpen: ((String) -> Unit)?,
    badges: List<Pair<String, FlowBadgeTone>>,
) {
    val edge = if (layout == FlowDisplayLayout.Row) FlowTokens.CoverEdge.Row else FlowTokens.CoverEdge.Tile
    val cover by rememberBookCover(book, maxEdge = edge)
    FlowDisplayCard(
        title = book.title,
        art = cover,
        onClick = { onOpen(book.bookId) },
        layout = layout,
        subtitle = subtitle,
        badges = badges.map { it.first },
        badgeTones = badges.toMap(),
        progress = book.readingProgress,
        corner = LinkedCorner.takeIf { book.sourceKind == BookSource.Linked.name },
        onLongClick = onLongOpen?.let { handler -> { handler(book.bookId) } },
    )
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
