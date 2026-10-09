package com.personal.flowreader.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** A schema-9 `progress` row (one row per book of every kind). */
data class LegacyProgressRow(
    val bookId: String,
    val title: String,
    val storedPath: String,
    val sourceUri: String,
    val sourceKind: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
    val inLibrary: Boolean,
    val readingProgress: Float,
    val libraryTabId: String,
    val anchorText: String,
    val chapterHref: String,
)

/** A schema-9 `que_items` row (pointed at a `progress` row by content id). */
data class LegacyQueRow(
    val id: String,
    val bookId: String,
    val sortOrder: Int,
    val addedAt: Long,
    val done: Boolean,
)

/** Plugin story waiting to move into its plugin's own database ([LegacyMigration.STAGING]). */
data class StagedPluginRow(
    val row: LegacyProgressRow,
    val localFilters: String,
)

class MigratedRows(
    val library: List<LibraryBookEntity>,
    val queue: List<QueueItemEntity>,
    val plugin: List<StagedPluginRow>,
)

/** Schema 9 (one shared `progress` table) to 10 (Library, Queue and per-plugin rows). */
object LegacyMigration {
    /** Plugin rows from schema 9, drained into `plugin_<id>.db` by `PluginStorageMigrator`. */
    const val STAGING = "legacy_plugin_progress"

    private val fileKinds = setOf(BookSource.Imported.name, BookSource.Linked.name)

    fun position(row: LegacyProgressRow) = ReadingPosition(
        chapterIndex = row.chapterIndex,
        chapterHref = row.chapterHref,
        blockIndex = row.blockIndex,
        charOffset = row.charOffset,
        anchorText = row.anchorText,
        fraction = row.readingProgress,
        positionAt = row.updatedAt,
        positionSessionAt = 0L,
        positionSource = PositionSource.Migration.name,
    )

    /**
     * Library books keep their row; every Queue entry gets its own copy of its book's file
     * reference, filters and position; plugin rows are staged. Queue rows without a book drop.
     */
    fun map(progress: List<LegacyProgressRow>, que: List<LegacyQueRow>, filters: Map<String, String>): MigratedRows {
        val byId = progress.associateBy { it.bookId }
        val library = progress.filter { it.inLibrary && it.sourceKind in fileKinds }.map { row ->
            LibraryBookEntity(
                bookId = row.bookId,
                title = row.title,
                storedPath = row.storedPath,
                sourceUri = row.sourceUri,
                sourceKind = row.sourceKind,
                shelfId = row.libraryTabId,
                addedAt = row.updatedAt,
                localFilters = filters[row.bookId].orEmpty(),
                position = position(row),
            )
        }
        val queue = que.mapNotNull { q ->
            val row = byId[q.bookId] ?: return@mapNotNull null
            val origin = when (row.sourceKind) {
                BookSource.Imported.name -> QueueOrigin.Text
                BookSource.Linked.name -> QueueOrigin.File
                else -> QueueOrigin.Plugin
            }
            QueueItemEntity(
                queId = q.id,
                bookId = row.bookId,
                title = row.title,
                storedPath = row.storedPath,
                sourceUri = row.sourceUri,
                sourceKind = row.sourceKind,
                origin = origin.name,
                sortOrder = q.sortOrder,
                addedAt = q.addedAt,
                done = q.done,
                localFilters = filters[row.bookId].orEmpty(),
                position = position(row),
            )
        }
        val plugin = progress.filter { it.sourceKind !in fileKinds }
            .map { StagedPluginRow(it, filters[it.bookId].orEmpty()) }
        return MigratedRows(library, queue, plugin)
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val progress = db.query("SELECT * FROM progress").use { c -> c.rows(::progressRow) }
            val que = db.query("SELECT * FROM que_items").use { c -> c.rows(::queRow) }
            val filters = db.query("SELECT bookId, rulesJson FROM book_filters").use { c ->
                c.rows { it.str("bookId") to it.str("rulesJson") }.toMap()
            }
            val rows = map(progress, que, filters)

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `library_books` (`bookId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`storedPath` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, `sourceKind` TEXT NOT NULL, " +
                    "`shelfId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `localFilters` TEXT NOT NULL, " +
                    POSITION_COLUMNS + ", PRIMARY KEY(`bookId`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `queue_items` (`queId` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `storedPath` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, " +
                    "`sourceKind` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `done` INTEGER NOT NULL, " +
                    "`doneAt` INTEGER NOT NULL, `localFilters` TEXT NOT NULL, " +
                    POSITION_COLUMNS + ", PRIMARY KEY(`queId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_items_bookId` ON `queue_items` (`bookId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_queue_items_sortOrder_done` ON `queue_items` (`sortOrder`, `done`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `queue_state` (`id` INTEGER NOT NULL, `currentQueId` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `$STAGING` (`bookId` TEXT NOT NULL PRIMARY KEY, `pluginId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `storedPath` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, " +
                    "`chapterIndex` INTEGER NOT NULL, `blockIndex` INTEGER NOT NULL, `charOffset` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `readingProgress` REAL NOT NULL, `anchorText` TEXT NOT NULL, " +
                    "`chapterHref` TEXT NOT NULL, `localFilters` TEXT NOT NULL)",
            )

            rows.library.forEach { b ->
                db.insert(
                    "library_books",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("bookId", b.bookId)
                        put("title", b.title)
                        put("storedPath", b.storedPath)
                        put("sourceUri", b.sourceUri)
                        put("sourceKind", b.sourceKind)
                        put("shelfId", b.shelfId)
                        put("addedAt", b.addedAt)
                        put("localFilters", b.localFilters)
                        putPosition(b.position)
                    },
                )
            }
            rows.queue.forEach { q ->
                db.insert(
                    "queue_items",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("queId", q.queId)
                        put("bookId", q.bookId)
                        put("title", q.title)
                        put("storedPath", q.storedPath)
                        put("sourceUri", q.sourceUri)
                        put("sourceKind", q.sourceKind)
                        put("sourceUrl", q.sourceUrl)
                        put("origin", q.origin)
                        put("sortOrder", q.sortOrder)
                        put("addedAt", q.addedAt)
                        put("done", if (q.done) 1 else 0)
                        put("doneAt", q.doneAt)
                        put("localFilters", q.localFilters)
                        putPosition(q.position)
                    },
                )
            }
            rows.plugin.forEach { p ->
                val r = p.row
                db.insert(
                    STAGING,
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("bookId", r.bookId)
                        put("pluginId", r.sourceKind)
                        put("title", r.title)
                        put("storedPath", r.storedPath)
                        put("sourceUri", r.sourceUri)
                        put("chapterIndex", r.chapterIndex)
                        put("blockIndex", r.blockIndex)
                        put("charOffset", r.charOffset)
                        put("updatedAt", r.updatedAt)
                        put("readingProgress", r.readingProgress)
                        put("anchorText", r.anchorText)
                        put("chapterHref", r.chapterHref)
                        put("localFilters", p.localFilters)
                    },
                )
            }
            db.execSQL("DROP TABLE IF EXISTS `progress`")
            db.execSQL("DROP TABLE IF EXISTS `que_items`")
            db.execSQL("DROP TABLE IF EXISTS `book_filters`")
        }
    }

    private const val POSITION_COLUMNS =
        "`chapterIndex` INTEGER NOT NULL, `chapterHref` TEXT NOT NULL, `blockIndex` INTEGER NOT NULL, " +
            "`charOffset` INTEGER NOT NULL, `anchorText` TEXT NOT NULL, `fraction` REAL NOT NULL, " +
            "`positionAt` INTEGER NOT NULL, `positionSessionAt` INTEGER NOT NULL, `positionSource` TEXT NOT NULL"

    private fun ContentValues.putPosition(p: ReadingPosition) {
        put("chapterIndex", p.chapterIndex)
        put("chapterHref", p.chapterHref)
        put("blockIndex", p.blockIndex)
        put("charOffset", p.charOffset)
        put("anchorText", p.anchorText)
        put("fraction", p.fraction)
        put("positionAt", p.positionAt)
        put("positionSessionAt", p.positionSessionAt)
        put("positionSource", p.positionSource)
    }

    fun progressRow(c: Cursor) = LegacyProgressRow(
        bookId = c.str("bookId"),
        title = c.str("title"),
        storedPath = c.str("storedPath"),
        sourceUri = c.str("sourceUri"),
        sourceKind = c.str("sourceKind"),
        chapterIndex = c.int("chapterIndex"),
        blockIndex = c.int("blockIndex"),
        charOffset = c.int("charOffset"),
        updatedAt = c.long("updatedAt"),
        inLibrary = c.int("inLibrary") != 0,
        readingProgress = c.getFloat(c.getColumnIndexOrThrow("readingProgress")),
        libraryTabId = c.str("libraryTabId"),
        anchorText = c.str("anchorText"),
        chapterHref = c.str("chapterHref"),
    )

    private fun queRow(c: Cursor) = LegacyQueRow(
        id = c.str("id"),
        bookId = c.str("bookId"),
        sortOrder = c.int("sortOrder"),
        addedAt = c.long("addedAt"),
        done = c.int("done") != 0,
    )
}

internal fun <T> Cursor.rows(read: (Cursor) -> T): List<T> {
    val out = ArrayList<T>(count.coerceAtLeast(0))
    while (moveToNext()) out += read(this)
    return out
}

internal fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column)).orEmpty()

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
