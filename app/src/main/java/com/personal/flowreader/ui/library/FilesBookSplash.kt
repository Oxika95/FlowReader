package com.personal.flowreader.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.design.card.media.FileMediaCardAdapter
import com.personal.flowreader.ui.design.card.media.FileMediaInfo
import com.personal.flowreader.ui.design.card.media.FlowMediaCard
import com.personal.flowreader.ui.design.card.media.MediaActionIds
import com.personal.flowreader.ui.design.card.media.withoutHostActions
import com.personal.flowreader.ui.theme.FlowTokens
import java.io.File

/** Files tab media card: cover + file metadata, Share / Remove / Open. */
@Composable
fun FilesBookSplash(
    book: BookItem?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    hiddenActions: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    val cover by rememberBookCover(book, maxEdge = FlowTokens.CoverEdge.Hero)
    val model = remember(book, busy, hiddenActions) {
        book?.let { FileMediaCardAdapter.model(fileMediaInfo(it), busy).withoutHostActions(hiddenActions) }
    }
    FlowMediaCard(
        visible = book != null,
        model = model,
        art = cover,
        onDismiss = onDismiss,
        onAction = { action ->
            val row = book ?: return@FlowMediaCard
            when (action.id) {
                MediaActionIds.SHARE -> shareBook(context, row)
                MediaActionIds.REMOVE -> {
                    onRemove(row.bookId)
                    onDismiss()
                }
                MediaActionIds.OPEN -> {
                    onOpen(row.bookId)
                    onDismiss()
                }
            }
        },
    )
}

private fun fileMediaInfo(book: BookItem): FileMediaInfo {
    val file = book.storedPath.takeIf { it.isNotBlank() }?.let(::File)
    return FileMediaInfo(
        title = book.title,
        sourceLabel = librarySourceLabel(book),
        linked = book.sourceKind == BookSource.Linked.name,
        lastRead = libraryLastRead(book),
        progress = book.readingProgress,
        format = file?.extension?.lowercase().orEmpty(),
        sizeBytes = file?.takeIf { it.isFile }?.length(),
    )
}

private fun shareBook(context: Context, book: BookItem) {
    val uri = book.sourceUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
    val send = Intent(Intent.ACTION_SEND).apply {
        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, book.title)
            putExtra(Intent.EXTRA_TEXT, book.title)
        }
    }
    context.startActivity(Intent.createChooser(send, "Share book"))
}
