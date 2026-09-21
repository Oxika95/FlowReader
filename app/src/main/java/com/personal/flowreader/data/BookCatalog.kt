package com.personal.flowreader.data

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
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

class BookCatalog(private val app: FlowApp) {
    private val cr get() = app.contentResolver

    suspend fun list(): List<ProgressEntity> = app.db.progress().library()

    /** Plugin tab membership (e.g. Royal Road). Not shown on Files. */
    suspend fun listPlugin(sourceKind: String): List<ProgressEntity> =
        app.db.progress().pluginLibrary(sourceKind)

    suspend fun listQue(): List<QueEntry> {
        val items = app.db.que().all()
        if (items.isEmpty()) return emptyList()
        val byId = app.db.progress().getMany(items.map { it.bookId }.distinct())
            .associateBy { it.bookId }
        return items.mapNotNull { item ->
            val progress = byId[item.bookId] ?: return@mapNotNull null
            QueEntry(item, progress)
        }
    }

    suspend fun getQue(id: String): QueItemEntity? = app.db.que().get(id)

    suspend fun nextUndoneQue(afterOrder: Int): QueEntry? {
        val item = app.db.que().nextUndone(afterOrder) ?: return null
        val progress = app.db.progress().get(item.bookId) ?: return null
        return QueEntry(item, progress)
    }

    suspend fun markQueDone(id: String) {
        val item = app.db.que().get(id) ?: return
        app.db.que().upsert(item.copy(done = true))
    }

    /**
     * Remove a Que row. Deletes the stored file only when the book is not in the
     * library and no other Que rows still reference it.
     */
    suspend fun removeQue(id: String) {
        val item = app.db.que().get(id) ?: return
        app.db.que().delete(id)
        val progress = app.db.progress().get(item.bookId) ?: return
        if (progress.inLibrary) return
        if (app.db.que().countForBook(item.bookId) > 0) return
        File(progress.storedPath).parentFile?.deleteRecursively()
        app.db.progress().delete(item.bookId)
        app.db.bookFilters().delete(item.bookId)
    }

    /**
     * Ingest shared plain text as a content-addressed TXT.
     *
     * Stored file bytes are the shared text as-is. [SharedTextTitle] derives a
     * display label from the first characters only and must not rewrite the body.
     *
     * @param inLibrary when true, show on Files; when false, only create progress if needed for Que
     * @param enqueue when true, append a new Que playlist row
     * @param displayTitle when set, used as the catalog title (plugins). Otherwise first chars of the body.
     */
    suspend fun addText(
        text: String,
        titleHint: String? = null,
        displayTitle: String? = null,
        inLibrary: Boolean,
        enqueue: Boolean,
    ): TextIngestResult {
        if (text.isBlank()) throw IllegalArgumentException("Nothing to share")

        val bytes = text.toByteArray(Charsets.UTF_8)
        val id = BookBytes.sha256(bytes)
        val existing = app.db.progress().get(id)
        val dest = destination(id, "txt", BookSource.Imported)
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)

        val title = displayTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: SharedTextTitle.from(text, titleHint)
        val now = System.currentTimeMillis()
        val row = ProgressEntity(
            bookId = id,
            title = existing?.title?.takeIf { it.isNotBlank() } ?: title,
            storedPath = dest.absolutePath,
            sourceUri = existing?.sourceUri.orEmpty(),
            sourceKind = BookSource.Imported.name,
            chapterIndex = existing?.chapterIndex ?: 0,
            blockIndex = existing?.blockIndex ?: 0,
            charOffset = existing?.charOffset ?: 0,
            updatedAt = now,
            inLibrary = (existing?.inLibrary == true) || inLibrary,
            readingProgress = existing?.readingProgress ?: 0f,
        )
        app.db.progress().upsert(row)

        val queItem = if (enqueue) {
            val order = app.db.que().maxSortOrder() + 1
            val item = QueItemEntity(
                id = UUID.randomUUID().toString(),
                bookId = id,
                sortOrder = order,
                addedAt = now,
                done = false,
            )
            app.db.que().upsert(item)
            item
        } else {
            null
        }
        return TextIngestResult(row, queItem)
    }

    /**
     * Upsert a plugin-backed book. [bookId] is stable (e.g. `rr:{fictionId}`) so
     * reopen and TTS streaming resume the same row. Not shown on Files.
     */
    suspend fun upsertPluginBook(
        bookId: String,
        title: String,
        sourceUri: String,
        sourceKind: String,
        text: String,
    ): ProgressEntity {
        val existing = app.db.progress().get(bookId)
        val dest = pluginBookFile(bookId)
        dest.writeText(text)
        val now = System.currentTimeMillis()
        val row = ProgressEntity(
            bookId = bookId,
            title = title,
            storedPath = dest.absolutePath,
            sourceUri = sourceUri,
            sourceKind = sourceKind,
            chapterIndex = existing?.chapterIndex ?: 0,
            blockIndex = existing?.blockIndex ?: 0,
            charOffset = existing?.charOffset ?: 0,
            updatedAt = now,
            inLibrary = false,
            readingProgress = existing?.readingProgress ?: 0f,
        )
        app.db.progress().upsert(row)
        return row
    }

    /**
     * Add a plugin fiction to its tab catalog without downloading chapters.
     * Preserves existing reading progress and chapter cache on disk.
     */
    suspend fun upsertPluginCatalogEntry(
        bookId: String,
        title: String,
        sourceUri: String,
        sourceKind: String,
        coverBytes: ByteArray? = null,
    ): ProgressEntity {
        val existing = app.db.progress().get(bookId)
        val dest = pluginBookFile(bookId)
        if (!dest.exists() || dest.length() == 0L) {
            dest.writeText(title)
        }
        if (coverBytes != null && coverBytes.isNotEmpty()) {
            writePluginCover(dest, coverBytes)
        }
        val now = System.currentTimeMillis()
        val row = ProgressEntity(
            bookId = bookId,
            title = title.ifBlank { existing?.title.orEmpty() }.ifBlank { "Royal Road" },
            storedPath = dest.absolutePath,
            sourceUri = sourceUri.ifBlank { existing?.sourceUri.orEmpty() },
            sourceKind = sourceKind,
            chapterIndex = existing?.chapterIndex ?: 0,
            blockIndex = existing?.blockIndex ?: 0,
            charOffset = existing?.charOffset ?: 0,
            updatedAt = now,
            inLibrary = false,
            readingProgress = existing?.readingProgress ?: 0f,
        )
        app.db.progress().upsert(row)
        return row
    }

    /**
     * Remove a book from the Files tab. If Que still references it, only clears
     * [ProgressEntity.inLibrary]. Otherwise deletes progress, filters, and the
     * imported/cache file tree (never the user's original SAF document).
     */
    suspend fun removeFromLibrary(bookId: String) {
        val progress = app.db.progress().get(bookId) ?: return
        if (app.db.que().countForBook(bookId) > 0) {
            app.db.progress().upsert(
                progress.copy(inLibrary = false, updatedAt = System.currentTimeMillis()),
            )
            return
        }
        val kind = runCatching { BookSource.valueOf(progress.sourceKind) }
            .getOrDefault(BookSource.Imported)
        val stored = File(progress.storedPath)
        when (kind) {
            BookSource.Imported -> stored.parentFile?.deleteRecursively()
            BookSource.Linked -> stored.parentFile?.deleteRecursively()
        }
        app.db.progress().delete(bookId)
        app.db.bookFilters().delete(bookId)
    }

    /**
     * Remove a plugin book from its tab. Keeps chapter cache under the plugin
     * files dir so a later re-add can resume. Drops Que rows for this book.
     */
    suspend fun removePluginMembership(bookId: String) {
        val progress = app.db.progress().get(bookId) ?: return
        app.db.que().deleteForBook(bookId)
        File(progress.storedPath).parentFile?.deleteRecursively()
        app.db.progress().delete(bookId)
        app.db.bookFilters().delete(bookId)
    }

    fun pluginCoverFile(storedPath: String): File? {
        val dir = File(storedPath).parentFile ?: return null
        return listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")
            .map { File(dir, it) }
            .firstOrNull { it.exists() && it.length() > 0L }
    }

    private fun pluginBookFile(bookId: String): File {
        val safe = bookId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(app.booksDir, safe).apply { mkdirs() }, "book.txt")
    }

    private fun writePluginCover(bookFile: File, bytes: ByteArray) {
        val dir = bookFile.parentFile ?: return
        listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp").forEach { name ->
            File(dir, name).delete()
        }
        val ext = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) -> "webp"
            else -> "jpg"
        }
        File(dir, "cover.$ext").writeBytes(bytes)
    }

    suspend fun enqueueExisting(bookId: String): QueItemEntity {
        app.db.progress().get(bookId)
            ?: throw IllegalArgumentException("Book not found")
        val order = app.db.que().maxSortOrder() + 1
        val item = QueItemEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            sortOrder = order,
            addedAt = System.currentTimeMillis(),
            done = false,
        )
        app.db.que().upsert(item)
        return item
    }

    suspend fun add(uri: Uri, source: BookSource): ProgressEntity {
        if (source == BookSource.Linked) persistReadAccess(uri)
        val tmp = File(app.cacheDir, "import-${UUID.randomUUID()}")
        try {
            cr.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalArgumentException("Could not read file")

            val id = BookBytes.sha256(tmp)
            val ext = BookBytes.extension(tmp)
            val existing = app.db.progress().get(id)
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
            val row = ProgressEntity(
                bookId = id,
                title = title,
                storedPath = dest.absolutePath,
                sourceUri = if (kind == BookSource.Linked) uri.toString() else "",
                sourceKind = kind.name,
                chapterIndex = existing?.chapterIndex ?: 0,
                blockIndex = existing?.blockIndex ?: 0,
                charOffset = existing?.charOffset ?: 0,
                updatedAt = System.currentTimeMillis(),
                inLibrary = true,
                readingProgress = existing?.readingProgress ?: 0f,
            )
            app.db.progress().upsert(row)
            return row
        } finally {
            tmp.delete()
        }
    }

    fun materialize(row: ProgressEntity): File {
        val kind = runCatching { BookSource.valueOf(row.sourceKind) }
            .getOrDefault(BookSource.Imported)
        val local = File(row.storedPath)
        return when (kind) {
            BookSource.Imported -> {
                if (!local.exists()) {
                    throw IllegalArgumentException("Imported file is missing")
                }
                local
            }
            BookSource.Linked -> {
                val uri = row.sourceUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
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
            val name = queryDisplayName(uri)
                ?.substringBeforeLast('.')
                ?.ifBlank { null }
            return TxtIngest.readText(name ?: "Text", file.readText()).title
        }
        return EpubIngest.read(file).title
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
