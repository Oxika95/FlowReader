package com.personal.flowreader.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.design.card.media.FileMediaCardAdapter
import com.personal.flowreader.ui.design.card.media.FileMediaInfo
import com.personal.flowreader.ui.design.card.media.FlowMediaCard
import com.personal.flowreader.ui.design.card.media.MediaActionIds
import com.personal.flowreader.ui.design.card.media.withoutHostActions
import com.personal.flowreader.ui.reader.TocOverlay
import com.personal.flowreader.ui.theme.FlowTokens
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files tab media card: cover rail Share / Remove / Folder / Contents; footer Read. */
@Composable
fun FilesBookSplash(
    book: BookItem?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenChapter: (bookId: String, chapterIndex: Int) -> Unit = { id, _ -> onOpen(id) },
    onRemove: (String) -> Unit,
    hiddenActions: Set<String> = emptySet(),
    folderError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as FlowApp
    val cover by rememberBookCover(book, maxEdge = FlowTokens.CoverEdge.Hero)
    val model = remember(book, busy, hiddenActions) {
        book?.let { FileMediaCardAdapter.model(fileMediaInfo(it), busy).withoutHostActions(hiddenActions) }
    }
    var showToc by remember(book?.bookId) { mutableStateOf(false) }
    var tocEntries by remember(book?.bookId) { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var tocRow by remember(book?.bookId) { mutableStateOf(0) }
    LaunchedEffect(book?.bookId, showToc) {
        val row = book ?: return@LaunchedEffect
        if (!showToc) return@LaunchedEffect
        val (entries, highlight) = withContext(Dispatchers.IO) {
            val entries = runCatching {
                val entity = app.db.library().get(row.bookId) ?: return@runCatching emptyList()
                ChapterSource.open(app.catalog.materialize(entity), row.title).tocEntries()
            }.getOrDefault(emptyList())
            val spine = row.chapterIndex.coerceAtLeast(0)
            val rowIndex = entries.indexOfFirst { it.first == spine }.takeIf { it >= 0 } ?: 0
            entries to rowIndex
        }
        tocEntries = entries
        tocRow = highlight.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
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
                MediaActionIds.FOLDER -> {
                    if (!openContainingFolder(context, row)) {
                        folderError("Could not open the containing folder")
                    }
                }
                MediaActionIds.TOC -> showToc = true
                MediaActionIds.OPEN, MediaActionIds.READ -> {
                    onOpen(row.bookId)
                    onDismiss()
                }
            }
        },
    )
    TocOverlay(
        visible = showToc && book != null,
        chapters = tocEntries.map { (_, label) -> label.ifBlank { "Chapter" } },
        chapterIndex = tocRow,
        onChapter = { rowIndex ->
            showToc = false
            val id = book?.bookId ?: return@TocOverlay
            val spine = tocEntries.getOrNull(rowIndex)?.first ?: rowIndex
            onOpenChapter(id, spine)
            onDismiss()
        },
        onDismiss = { showToc = false },
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

/** Best-effort open of the file's parent folder in a documents UI. */
internal fun openContainingFolder(context: Context, book: BookItem): Boolean {
    val uri = book.sourceUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: book.storedPath.takeIf { it.isNotBlank() }?.let { Uri.fromFile(File(it)) }
        ?: return false
    return runCatching {
        when (uri.scheme) {
            "file" -> {
                val parent = File(uri.path ?: return@runCatching false).parentFile ?: return@runCatching false
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(parent), DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
            "content" -> {
                val parent = parentDocumentUri(context, uri) ?: return@runCatching false
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(parent, DocumentsContract.Document.MIME_TYPE_DIR)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
            else -> return@runCatching false
        }
        true
    }.getOrDefault(false)
}

private fun parentDocumentUri(context: Context, uri: Uri): Uri? {
    if (!DocumentsContract.isDocumentUri(context, uri)) return null
    val docId = DocumentsContract.getDocumentId(uri)
    val colon = docId.indexOf(':')
    if (colon < 0) return null
    val authority = uri.authority ?: return null
    val volume = docId.substring(0, colon)
    val path = docId.substring(colon + 1)
    val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "").trimEnd('/')
    val parentId = if (parentPath.isEmpty()) "$volume:" else "$volume:$parentPath"
    return DocumentsContract.buildDocumentUri(authority, parentId)
}
