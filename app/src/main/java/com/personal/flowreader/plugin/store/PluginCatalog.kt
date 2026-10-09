package com.personal.flowreader.plugin.store

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.data.PositionSource
import com.personal.flowreader.data.ProgressUpdate
import com.personal.flowreader.data.ReadingSessionId
import com.personal.flowreader.data.PositionDomain
import java.io.File
import kotlinx.coroutines.CompletableDeferred

fun PluginBookEntity.toItem(pluginId: String) = BookItem(
    bookId = bookId,
    title = title,
    storedPath = storedPath,
    sourceUri = workUrl,
    sourceKind = pluginId,
    readingProgress = position.fraction,
    lastReadAt = maxOf(addedAt, position.positionAt),
)

/** Plugin library rows, each plugin in its own database ([PluginDatabases]). */
class PluginCatalog(private val app: FlowApp) {
    private val migrated = CompletableDeferred<Unit>()

    /** Called once staged schema-9 rows are moved in (or that failed); reads wait for it. */
    fun markMigrated() {
        migrated.complete(Unit)
    }

    private suspend fun dao(pluginId: String): PluginBookDao {
        migrated.await()
        return app.pluginDbs.books(pluginId)
    }

    /** Plugin id owning [bookId], by its prefix (installed plugins only). */
    fun pluginIdFor(bookId: String): String? = app.pluginManager.resolveBookId(bookId)?.first?.id

    private fun workIdFor(bookId: String): String = bookId.substringAfter(':')

    suspend fun get(bookId: String): PluginBookEntity? {
        val pluginId = pluginIdFor(bookId) ?: return null
        return dao(pluginId).get(bookId)
    }

    suspend fun list(pluginId: String): List<PluginBookEntity> = dao(pluginId).all()

    /** Library row for a story, without downloading chapters. Keeps position, filters, cover. */
    suspend fun upsertCatalogEntry(
        pluginId: String,
        bookId: String,
        title: String,
        sourceUri: String,
        coverBytes: ByteArray? = null,
    ): PluginBookEntity {
        val dest = snapshotFile(bookId)
        if (!dest.exists() || dest.length() == 0L) dest.writeText(title)
        if (coverBytes != null && coverBytes.isNotEmpty()) writeCover(dest, coverBytes)
        return ensureRow(pluginId, bookId, title, sourceUri, dest)
    }

    /**
     * Library row for a story being opened, with a text snapshot. [startChapter] (ToC index)
     * moves the saved position to that chapter's start when it differs from the saved chapter.
     */
    suspend fun upsertBook(
        pluginId: String,
        bookId: String,
        title: String,
        sourceUri: String,
        text: String,
        startChapter: Int? = null,
        chapterHref: String = "",
        chapterCount: Int = 0,
    ): PluginBookEntity {
        val dest = snapshotFile(bookId)
        dest.writeText(text)
        val row = ensureRow(pluginId, bookId, title, sourceUri, dest)
        if (startChapter != null && startChapter != row.position.chapterIndex) {
            val update = ProgressUpdate(
                session = ReadingSessionId(PositionDomain.Plugin, bookId),
                rowKey = bookId,
                chapterIndex = startChapter,
                blockIndex = 0,
                charOffset = 0,
                fraction = if (chapterCount > 0) startChapter.toFloat() / chapterCount else 0f,
                at = System.currentTimeMillis(),
                chapterHref = chapterHref,
                source = PositionSource.PluginSeek,
            )
            app.positionLog.position(update, writePosition(update))
            return dao(pluginId).get(bookId) ?: row
        }
        return row
    }

    private suspend fun ensureRow(pluginId: String, bookId: String, title: String, sourceUri: String, dest: File): PluginBookEntity {
        val dao = dao(pluginId)
        val existing = dao.get(bookId)
        if (existing == null) {
            dao.insert(
                PluginBookEntity(
                    bookId = bookId,
                    workId = workIdFor(bookId),
                    title = title.ifBlank { bookId },
                    storedPath = dest.absolutePath,
                    workUrl = sourceUri,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            dao.updateCatalog(
                bookId,
                title.ifBlank { existing.title }.ifBlank { bookId },
                sourceUri.ifBlank { existing.workUrl },
                dest.absolutePath,
            )
        }
        return dao.get(bookId) ?: error("Could not store $bookId")
    }

    suspend fun writePosition(update: ProgressUpdate): Boolean {
        val pluginId = pluginIdFor(update.rowKey) ?: return false
        val p = update.toPosition()
        return dao(pluginId).writePosition(
            update.rowKey, p.chapterIndex, p.chapterHref, p.blockIndex, p.charOffset, p.anchorText,
            p.fraction, p.positionAt, p.positionSessionAt, p.positionSource,
        ) > 0
    }

    suspend fun setLocalFilters(bookId: String, json: String) {
        val pluginId = pluginIdFor(bookId) ?: return
        dao(pluginId).setLocalFilters(bookId, json)
    }

    /** Remove a story from its tab; its Queue entries go too. Keeps the chapter cache. */
    suspend fun removeMembership(bookId: String) {
        val pluginId = pluginIdFor(bookId)
        app.db.queue().deleteForBook(bookId)
        snapshotFile(bookId).parentFile?.deleteRecursively()
        if (pluginId != null) dao(pluginId).delete(bookId)
    }

    fun coverFile(storedPath: String): File? {
        val dir = File(storedPath).parentFile ?: return null
        return COVER_NAMES.map { File(dir, it) }.firstOrNull { it.exists() && it.length() > 0L }
    }

    fun snapshotFile(bookId: String): File {
        val safe = bookId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(app.booksDir, safe).apply { mkdirs() }, "book.txt")
    }

    private fun writeCover(bookFile: File, bytes: ByteArray) {
        val dir = bookFile.parentFile ?: return
        COVER_NAMES.forEach { File(dir, it).delete() }
        val ext = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) -> "webp"
            else -> "jpg"
        }
        File(dir, "cover.$ext").writeBytes(bytes)
    }

    private companion object {
        val COVER_NAMES = listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")
    }
}
