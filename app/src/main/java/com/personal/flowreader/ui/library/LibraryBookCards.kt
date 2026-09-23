package com.personal.flowreader.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.library.plugin.royalroad.RoyalRoadPlugin
import com.personal.flowreader.ui.common.loadBookCoverBitmap
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.reader.ReaderPanelShape
import com.personal.flowreader.ui.theme.FlowTokens

@Composable
fun LibraryBooksPane(
    books: List<ProgressEntity>,
    viewMode: LibraryViewMode,
    busy: Boolean,
    inset: Dp,
    emptyMessage: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitleFor: (ProgressEntity) -> String = { libraryBookSubtitle(it) },
    onLongOpen: ((String) -> Unit)? = null,
) {
    Box(modifier.fillMaxSize()) {
        if (books.isEmpty() && !busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = inset),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            when (viewMode) {
                LibraryViewMode.List -> LibraryBookList(books, inset, onOpen, subtitleFor, onLongOpen)
                LibraryViewMode.Shelf -> LibraryBookShelf(books, inset, onOpen, onLongOpen)
            }
        }
    }
}

@Composable
fun LibraryBookList(
    books: List<ProgressEntity>,
    inset: Dp,
    onOpen: (String) -> Unit,
    subtitleFor: (ProgressEntity) -> String = { libraryBookSubtitle(it) },
    onLongOpen: ((String) -> Unit)? = null,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = inset,
            top = inset,
            end = inset,
            bottom = inset + FlowTokens.FabClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.bookId }) { book ->
            LibraryBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FlowTokens.Comp.ListRow),
                onClick = { onOpen(book.bookId) },
                onLongClick = onLongOpen?.let { handler -> { handler(book.bookId) } },
            ) {
                LibraryBookDetailsRow(book, subtitleFor(book))
            }
        }
    }
}

@Composable
fun LibraryBookShelf(
    books: List<ProgressEntity>,
    inset: Dp,
    onOpen: (String) -> Unit,
    onLongOpen: ((String) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(FlowTokens.Comp.GridMinCell),
        contentPadding = PaddingValues(
            start = inset,
            top = inset,
            end = inset,
            bottom = inset + FlowTokens.FabClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.bookId }) { book ->
            LibraryBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(FlowTokens.CoverAspect),
                onClick = { onOpen(book.bookId) },
                onLongClick = onLongOpen?.let { handler -> { handler(book.bookId) } },
            ) {
                LibraryBookShelfTile(book)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    Card(
        modifier = modifier.then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            } else {
                Modifier.clickable(onClick = onClick)
            },
        ),
        shape = ReaderPanelShape,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = onBg),
        elevation = CardDefaults.cardElevation(defaultElevation = FlowTokens.Radius.None),
        border = BorderStroke(FlowTokens.Stroke.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        content()
    }
}

@Composable
private fun BookProgressBar(progress: Float, modifier: Modifier = Modifier) {
    com.personal.flowreader.ui.common.BookProgressBar(
        progress = progress,
        modifier = modifier,
        variant = com.personal.flowreader.ui.common.BookProgressVariant.Standard,
    )
}

@Composable
private fun LibraryBookDetailsRow(book: ProgressEntity, subtitle: String) {
    val cover by rememberLibraryCover(book, maxEdge = 384)
    val linked = book.sourceKind == BookSource.Linked.name
    val cardBg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = FlowTokens.Comp.ProgressBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(FlowTokens.CoverAspect),
            ) {
                LibraryCoverFill(
                    book = book,
                    cover = cover,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.4f to Color.Transparent,
                                    1f to cardBg,
                                ),
                            ),
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = FlowTokens.Space.M, end = FlowTokens.Space.L),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (linked) {
            Icon(
                Icons.Filled.Link,
                contentDescription = "Linked file",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(FlowTokens.Space.S)
                    .size(FlowTokens.Icon.S),
            )
        }
        BookProgressBar(
            progress = book.readingProgress,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LibraryBookShelfTile(book: ProgressEntity) {
    val cover by rememberLibraryCover(book, maxEdge = 512)
    val linked = book.sourceKind == BookSource.Linked.name
    Box(Modifier.fillMaxSize()) {
        LibraryCoverFill(
            book = book,
            cover = cover,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FlowTokens.ShelfGradientHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                FlowTokens.CoverBandBlack,
                            ),
                        ),
                    ),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(FlowTokens.CoverBandBlack),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = FlowTokens.Space.M,
                        end = FlowTokens.Space.M,
                        top = FlowTokens.Space.Hair,
                        bottom = FlowTokens.Space.S,
                    ),
                )
                BookProgressBar(progress = book.readingProgress)
            }
        }
        if (linked) {
            Icon(
                Icons.Filled.Link,
                contentDescription = "Linked file",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(FlowTokens.Space.S)
                    .size(FlowTokens.Icon.S),
            )
        }
    }
}

@Composable
fun rememberLibraryCover(book: ProgressEntity, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
    rememberBookCover(book, maxEdge)

internal fun loadLibraryCoverBitmap(book: ProgressEntity, maxEdge: Int): android.graphics.Bitmap? =
    loadBookCoverBitmap(book, maxEdge)

@Composable
private fun LibraryCoverFill(
    book: ProgressEntity,
    cover: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                book.title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun librarySourceLabel(book: ProgressEntity): String =
    when (book.sourceKind) {
        RoyalRoadPlugin.ID -> "Royal Road"
        else -> runCatching { BookSource.valueOf(book.sourceKind).label }
            .getOrDefault(BookSource.Imported.label)
    }

internal fun libraryBookSubtitle(book: ProgressEntity): String {
    val source = librarySourceLabel(book)
    val whenRead = DateUtils.getRelativeTimeSpanString(
        book.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "$source · $whenRead"
}
