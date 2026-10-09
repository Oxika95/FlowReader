package com.personal.flowreader.data

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.personal.flowreader.FlowApp
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

object BookBytes {
    fun looksLikeTxt(head: ByteArray): Boolean {
        if (head.isEmpty()) return false
        val asText = head.decodeToString()
        return !asText.startsWith("PK") && head[0] != 0.toByte()
    }

    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun extension(file: File): String {
        val head = ByteArray(4)
        file.inputStream().use { it.read(head) }
        return if (looksLikeTxt(head)) "txt" else "epub"
    }
}

object FileAccessAdvice {
    fun forSdk(sdk: Int = Build.VERSION.SDK_INT): String = when {
        sdk >= 33 ->
            "On Android 13+, photo and media permissions do not cover EPUB or TXT. The system file picker grants access to only the file you choose. Flow Reader will not ask for all-files access."
        sdk >= 30 ->
            "On Android 11+, storage is scoped. The system file picker is the supported way to open books. All-files access is for file managers, not readers."
        sdk >= 29 ->
            "On Android 10, apps use the system file picker instead of broad storage permission. You only grant the file you pick."
        else ->
            "The system file picker grants access to the file you choose. Flow Reader does not need a storage permission."
    }
}

/** Display title for shared/clipboard text. Does not alter the stored body. */
object SharedTextTitle {
    private const val FALLBACK = "Shared text"
    private const val WINDOW = 32

    fun from(text: String, hint: String? = null): String {
        hint?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(WINDOW) }

        val title = buildString {
            var pendingSpace = false
            for (ch in text.take(WINDOW)) {
                when {
                    ch.isLetterOrDigit() -> {
                        if (pendingSpace && isNotEmpty()) append(' ')
                        pendingSpace = false
                        append(ch)
                    }
                    ch == ',' || ch == '.' || ch == '!' || ch == '?' -> {
                        if (pendingSpace && isNotEmpty()) append(' ')
                        pendingSpace = false
                        append(ch)
                    }
                    ch.isWhitespace() -> pendingSpace = true
                    // Drop other characters; do not join adjacent words.
                    else -> pendingSpace = true
                }
            }
        }.trim()
        return title.ifEmpty { FALLBACK }
    }
}

/** What the reader needs to open one book (a Library file or a plugin story) at its own row. */
data class ReaderRow(
    val session: ReadingSessionId,
    val title: String,
    val storedPath: String,
    val sourceUri: String,
    val sourceKind: String,
    val position: ReadingPosition,
    val localFilters: String,
)

/** Library (Files, shelves) and Queue rows, and the book files behind them. */
class BookCatalog(private val app: FlowApp) {
    private val cr get() = app.contentResolver
    private val library get() = app.db.library()
    private val queue get() = app.db.queue()

    suspend fun list(): List<BookItem> = library.shelf("").map { it.toItem() }

    suspend fun listTab(tabId: String): List<BookItem> = library.shelf(tabId).map { it.toItem() }

    suspend fun clearLibraryTab(tabId: String) = library.clearShelf(tabId)

    suspend fun libraryBook(bookId: String): LibraryBookEntity? = library.get(bookId)

    /** Card data for [bookId]: its Library row, else its first Queue entry. */
    suspend fun cardItem(bookId: String): BookItem? =
        library.get(bookId)?.toItem() ?: queue.all().firstOrNull { it.bookId == bookId }?.toItem()

    /** Library file or plugin story [bookId], ready to open in the reader. */
    suspend fun readerRow(bookId: String): ReaderRow? {
        app.pluginCatalog.get(bookId)?.let { p ->
            return ReaderRow(
                session = ReadingSessionId(PositionDomain.Plugin, bookId),
                title = p.title,
                storedPath = p.storedPath,
                sourceUri = p.workUrl,
                sourceKind = app.pluginCatalog.pluginIdFor(bookId).orEmpty(),
                position = p.position,
                localFilters = p.localFilters,
            )
        }
        val b = library.get(bookId) ?: return null
        return ReaderRow(
            session = ReadingSessionId(PositionDomain.Library, bookId),
            title = b.title,
            storedPath = b.storedPath,
            sourceUri = b.sourceUri,
            sourceKind = b.sourceKind,
            position = b.position,
            localFilters = b.localFilters,
        )
    }

    /** Local filter rules (JSON, blank = none) of the row [domain] / [key]. */
    suspend fun setLocalFilters(domain: PositionDomain, key: String, json: String) {
        when (domain) {
            PositionDomain.Library -> library.setLocalFilters(key, json)
            PositionDomain.Queue -> queue.setLocalFilters(key, json)
            PositionDomain.Plugin -> app.pluginCatalog.setLocalFilters(key, json)
        }
    }

    // --- Queue ------------------------------------------------------------------------------

    suspend fun listQue(): List<QueueItemEntity> = queue.all()

    suspend fun getQue(queId: String): QueueItemEntity? = queue.get(queId)

    suspend fun markQueDone(queId: String) = queue.markDone(queId, System.currentTimeMillis())

    /** Rows in [ids] take sortOrder 0, 1, 2… in that order; rows not listed keep theirs. */
    suspend fun reorderQue(ids: List<String>) {
        app.db.withTransaction {
            ids.forEachIndexed { i, id -> queue.setSortOrder(id, i) }
        }
    }

    /** Remove a Queue entry; its stored file goes too when nothing else uses it. */
    suspend fun removeQue(queId: String) {
        val item = queue.get(queId) ?: return
        queue.delete(queId)
        app.positionLog.event("QUEUE_REMOVE", "que" to queId, "book" to item.bookId)
        if (app.pluginBooks.isPluginBook(item.bookId)) return
        deleteFileIfUnused(item.storedPath, item.sourceKind)
    }

    /** Queue entry for a Library book or plugin story, starting where that book is. */
    suspend fun enqueueExisting(bookId: String): QueueItemEntity {
        val plugin = app.pluginCatalog.get(bookId)
        val item = if (plugin != null) {
            newQueueItem(
                bookId = bookId,
                title = plugin.title,
                storedPath = plugin.storedPath,
                sourceUri = plugin.workUrl,
                sourceKind = app.pluginCatalog.pluginIdFor(bookId).orEmpty(),
                origin = QueueOrigin.Plugin,
                position = plugin.position,
            )
        } else {
            val b = library.get(bookId) ?: throw IllegalArgumentException("Book not found")
            newQueueItem(
                bookId = bookId,
                title = b.title,
                storedPath = b.storedPath,
                sourceUri = b.sourceUri,
                sourceKind = b.sourceKind,
                origin = QueueOrigin.Library,
                position = b.position,
                localFilters = b.localFilters,
            )
        }
        return insertQueue(item)
    }

    private suspend fun newQueueItem(
        bookId: String,
        title: String,
        storedPath: String,
        sourceUri: String,
        sourceKind: String,
        origin: QueueOrigin,
        sourceUrl: String = "",
        position: ReadingPosition = ReadingPosition(),
        localFilters: String = "",
    ): QueueItemEntity = QueueItemEntity(
        queId = UUID.randomUUID().toString(),
        bookId = bookId,
        title = title,
        storedPath = storedPath,
        sourceUri = sourceUri,
        sourceKind = sourceKind,
        sourceUrl = sourceUrl,
        origin = origin.name,
        sortOrder = queue.maxSortOrder() + 1,
        addedAt = System.currentTimeMillis(),
        localFilters = localFilters,
        position = position,
    )

    private suspend fun insertQueue(item: QueueItemEntity): QueueItemEntity {
        queue.insert(item)
        app.positionLog.event(
            "QUEUE_ADD",
            "que" to item.queId,
            "book" to item.bookId,
            "origin" to item.origin,
            "url" to item.sourceUrl,
            "title" to item.title,
            "via" to PositionLog.callers(),
        )
        return item
    }

    // --- Ingest -----------------------------------------------------------------------------

    /**
     * Ingest shared plain text as a content-addressed TXT.
     *
     * Stored file bytes are the shared text as-is. [SharedTextTitle] derives a
     * display label from the first characters only and must not rewrite the body.
     *
     * @param inLibrary when true, show on Files (or [libraryTabId])
     * @param enqueue when true, append a new Queue entry
     * @param displayTitle when set, used as the catalog title (plugins). Otherwise first chars of the body.
     * @param sourceUrl web page the text came from
     */
    suspend fun addText(
        text: String,
        titleHint: String? = null,
        displayTitle: String? = null,
        inLibrary: Boolean,
        enqueue: Boolean,
        libraryTabId: String = "",
        sourceUrl: String = "",
    ): TextIngestResult {
        if (text.isBlank()) throw IllegalArgumentException("Nothing to share")
        val title = displayTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: SharedTextTitle.from(text, titleHint)
        return addBytes(text.toByteArray(Charsets.UTF_8), "txt", title, inLibrary, enqueue, libraryTabId, sourceUrl)
    }

    /** Ingest a generated EPUB (web crawl) the same way as [addText]. */
    suspend fun addEpub(
        bytes: ByteArray,
        title: String,
        inLibrary: Boolean,
        enqueue: Boolean,
        libraryTabId: String = "",
        sourceUrl: String = "",
    ): TextIngestResult = addBytes(bytes, "epub", title, inLibrary, enqueue, libraryTabId, sourceUrl)

    private suspend fun addBytes(
        bytes: ByteArray,
        ext: String,
        title: String,
        inLibrary: Boolean,
        enqueue: Boolean,
        libraryTabId: String,
        sourceUrl: String,
    ): TextIngestResult {
        val id = BookBytes.sha256(bytes)
        val dest = destination(id, ext, BookSource.Imported)
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        if (ext == "epub") EpubCover.ensureCached(dest)

        var shownTitle = title
        if (inLibrary) {
            val shelf = libraryTabId.trim()
            val existing = library.get(id)
            if (existing == null) {
                library.insert(
                    LibraryBookEntity(
                        bookId = id,
                        title = title,
                        storedPath = dest.absolutePath,
                        sourceUri = sourceUrl,
                        sourceKind = BookSource.Imported.name,
                        shelfId = shelf,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                shownTitle = existing.title.ifBlank { title }
                library.updateFile(
                    id,
                    shownTitle,
                    dest.absolutePath,
                    existing.sourceUri.ifBlank { sourceUrl },
                    existing.sourceKind,
                    shelf.ifEmpty { existing.shelfId },
                )
            }
        }
        val queItem = if (enqueue) {
            insertQueue(
                newQueueItem(
                    bookId = id,
                    title = title,
                    storedPath = dest.absolutePath,
                    sourceUri = "",
                    sourceKind = BookSource.Imported.name,
                    origin = if (sourceUrl.isNotBlank()) QueueOrigin.Web else QueueOrigin.Text,
                    sourceUrl = sourceUrl,
                ),
            )
        } else {
            null
        }
        return TextIngestResult(id, shownTitle, queItem)
    }

    suspend fun add(uri: Uri, source: BookSource, libraryTabId: String = ""): LibraryBookEntity {
        if (source == BookSource.Linked) persistReadAccess(uri)
        val tmp = File(app.cacheDir, "import-${UUID.randomUUID()}")
        try {
            cr.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalArgumentException("Could not read file")

            val id = BookBytes.sha256(tmp)
            val ext = BookBytes.extension(tmp)
            val existing = library.get(id)
            val kind = when {
                source == BookSource.Imported -> BookSource.Imported
                existing?.sourceKind == BookSource.Imported.name -> BookSource.Imported
                else -> BookSource.Linked
            }
            val dest = destination(id, ext, kind)
            dest.parentFile?.mkdirs()
            tmp.copyTo(dest, overwrite = true)
            if (ext.equals("epub", ignoreCase = true)) {
                EpubCover.ensureCached(dest)
            }

            val title = ingestTitle(dest, ext, uri)
            val shelf = libraryTabId.trim()
            val sourceUri = if (kind == BookSource.Linked) uri.toString() else ""
            if (existing == null) {
                library.insert(
                    LibraryBookEntity(
                        bookId = id,
                        title = title,
                        storedPath = dest.absolutePath,
                        sourceUri = sourceUri,
                        sourceKind = kind.name,
                        shelfId = shelf,
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                library.updateFile(id, title, dest.absolutePath, sourceUri, kind.name, shelf.ifEmpty { existing.shelfId })
            }
            return library.get(id) ?: throw IllegalStateException("Could not store book")
        } finally {
            tmp.delete()
        }
    }

    /** Remove a book from the Files tab. Its file goes too when no Queue entry uses it. */
    suspend fun removeFromLibrary(bookId: String) {
        val book = library.get(bookId) ?: return
        library.delete(bookId)
        deleteFileIfUnused(book.storedPath, book.sourceKind)
    }

    /** Imported copy or linked-cache folder, once no Library or Queue row points at it. */
    private suspend fun deleteFileIfUnused(storedPath: String, sourceKind: String) {
        if (storedPath.isBlank()) return
        if (library.countForPath(storedPath) > 0 || queue.countForPath(storedPath) > 0) return
        if (sourceKind != BookSource.Imported.name && sourceKind != BookSource.Linked.name) return
        File(storedPath).parentFile?.deleteRecursively()
    }

    fun materialize(row: LibraryBookEntity): File = materialize(row.storedPath, row.sourceUri, row.sourceKind)

    fun materialize(row: QueueItemEntity): File = materialize(row.storedPath, row.sourceUri, row.sourceKind)

    fun materialize(row: ReaderRow): File = materialize(row.storedPath, row.sourceUri, row.sourceKind)

    private fun materialize(storedPath: String, sourceUri: String, sourceKind: String): File {
        val kind = runCatching { BookSource.valueOf(sourceKind) }
            .getOrDefault(BookSource.Imported)
        val local = File(storedPath)
        return when (kind) {
            BookSource.Imported -> {
                if (!local.exists()) {
                    throw IllegalArgumentException("Imported file is missing")
                }
                local
            }
            BookSource.Linked -> {
                val uri = sourceUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                    ?: throw IllegalArgumentException("Linked file has no location")
                if (local.exists() && !isDocumentNewer(uri, local.lastModified())) {
                    return local
                }
                cr.openInputStream(uri)?.use { input ->
                    local.parentFile?.mkdirs()
                    local.outputStream().use { input.copyTo(it) }
                } ?: throw IllegalArgumentException(
                    "This linked file is no longer accessible. Re-add it, or import a copy.",
                )
                if (local.extension.equals("epub", ignoreCase = true)) {
                    EpubCover.invalidate(local)
                    EpubCover.ensureCached(local)
                }
                local
            }
        }
    }

    private fun destination(id: String, ext: String, kind: BookSource): File {
        val dir = when (kind) {
            BookSource.Imported -> File(app.booksDir, id)
            BookSource.Linked -> File(app.cacheDir, "linked/$id")
        }
        dir.mkdirs()
        return File(dir, "book.$ext")
    }

    private fun ingestTitle(file: File, ext: String, uri: Uri): String {
        if (ext == "txt") {
            return queryDisplayName(uri)
                ?.substringBeforeLast('.')
                ?.ifBlank { null }
                ?: "Text"
        }
        return EpubIngest.readTitle(file)
    }

    private fun persistReadAccess(uri: Uri) {
        try {
            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            throw IllegalArgumentException(
                "This location does not allow lasting access. Import a copy instead.",
                e,
            )
        }
    }

    private fun isDocumentNewer(uri: Uri, cacheModified: Long): Boolean {
        val modified = documentLastModified(uri) ?: return false
        return modified > cacheModified
    }

    private fun documentLastModified(uri: Uri): Long? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return runCatching {
            cr.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idx < 0) null else cursor.getLong(idx)
            }
        }.getOrNull()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx < 0) null else cursor.getString(idx)
            }
        }.getOrNull()
    }
}
