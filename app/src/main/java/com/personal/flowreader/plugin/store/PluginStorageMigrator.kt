package com.personal.flowreader.plugin.store

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.LegacyMigration
import com.personal.flowreader.data.PositionSource
import com.personal.flowreader.data.ReadingPosition
import com.personal.flowreader.data.long
import com.personal.flowreader.data.rows
import com.personal.flowreader.data.str
import com.personal.flowreader.data.int

/**
 * Moves plugin stories staged by the schema 9 → 10 migration into each plugin's own database.
 * A row is deleted from staging only after its plugin database has it, so an interrupted run
 * resumes on the next launch. Rows of plugins that aren't installed stay staged.
 */
class PluginStorageMigrator(private val app: FlowApp) {
    private class Staged(
        val bookId: String,
        val pluginId: String,
        val title: String,
        val storedPath: String,
        val sourceUri: String,
        val position: ReadingPosition,
        val localFilters: String,
    )

    suspend fun run() {
        val db = app.db.openHelper.writableDatabase
        val exists = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(LegacyMigration.STAGING),
        ).use { it.moveToFirst() }
        if (!exists) return
        val staged = db.query("SELECT * FROM ${LegacyMigration.STAGING}").use { c ->
            c.rows {
                Staged(
                    bookId = it.str("bookId"),
                    pluginId = it.str("pluginId"),
                    title = it.str("title"),
                    storedPath = it.str("storedPath"),
                    sourceUri = it.str("sourceUri"),
                    position = ReadingPosition(
                        chapterIndex = it.int("chapterIndex"),
                        chapterHref = it.str("chapterHref"),
                        blockIndex = it.int("blockIndex"),
                        charOffset = it.int("charOffset"),
                        anchorText = it.str("anchorText"),
                        fraction = it.getFloat(it.getColumnIndexOrThrow("readingProgress")),
                        positionAt = it.long("updatedAt"),
                        positionSource = PositionSource.Migration.name,
                    ),
                    localFilters = it.str("localFilters"),
                )
            }
        }
        val installed = app.pluginManager.ids()
        staged.filter { it.pluginId in installed }.forEach { row ->
            val dao = app.pluginDbs.books(row.pluginId)
            if (dao.get(row.bookId) == null) {
                dao.insert(
                    PluginBookEntity(
                        bookId = row.bookId,
                        workId = row.bookId.substringAfter(':'),
                        title = row.title,
                        storedPath = row.storedPath,
                        workUrl = row.sourceUri,
                        addedAt = row.position.positionAt,
                        localFilters = row.localFilters,
                        position = row.position,
                    ),
                )
            }
            db.execSQL("DELETE FROM ${LegacyMigration.STAGING} WHERE bookId = ?", arrayOf(row.bookId))
            app.positionLog.event("MIGRATE", "plugin" to row.pluginId, "book" to row.bookId, "chapter" to row.position.chapterIndex)
        }
        val left = db.query("SELECT COUNT(*) FROM ${LegacyMigration.STAGING}").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        if (left == 0) db.execSQL("DROP TABLE IF EXISTS ${LegacyMigration.STAGING}")
    }
}
