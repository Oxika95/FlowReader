package com.personal.flowreader.plugin.store

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.flowreader.data.ReadingPosition
import kotlinx.coroutines.flow.Flow

/** A story in one plugin's library (its own database, `plugin_<id>.db`). */
@Entity(tableName = "plugin_books")
data class PluginBookEntity(
    /** `{bookIdPrefix}:{workId}`. */
    @PrimaryKey val bookId: String,
    val workId: String,
    val title: String,
    /** Snapshot file whose folder holds the downloaded cover. */
    val storedPath: String,
    val workUrl: String = "",
    val addedAt: Long,
    val localFilters: String = "",
    @Embedded val position: ReadingPosition = ReadingPosition(),
)

@Dao
interface PluginBookDao {
    @Query("SELECT * FROM plugin_books WHERE bookId = :bookId")
    suspend fun get(bookId: String): PluginBookEntity?

    @Query("SELECT * FROM plugin_books ORDER BY MAX(addedAt, positionAt) DESC")
    suspend fun all(): List<PluginBookEntity>

    /** Returns -1 when the row already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: PluginBookEntity): Long

    @Query("UPDATE plugin_books SET title = :title, workUrl = :workUrl, storedPath = :storedPath WHERE bookId = :bookId")
    suspend fun updateCatalog(bookId: String, title: String, workUrl: String, storedPath: String)

    @Query("UPDATE plugin_books SET localFilters = :json WHERE bookId = :bookId")
    suspend fun setLocalFilters(bookId: String, json: String): Int

    @Query(
        "UPDATE plugin_books SET chapterIndex = :chapterIndex, chapterHref = :chapterHref, " +
            "blockIndex = :blockIndex, charOffset = :charOffset, anchorText = :anchorText, fraction = :fraction, " +
            "positionAt = :positionAt, positionSessionAt = :positionSessionAt, positionSource = :positionSource, " +
            "chapterFinished = :chapterFinished WHERE bookId = :bookId",
    )
    suspend fun writePosition(
        bookId: String,
        chapterIndex: Int,
        chapterHref: String,
        blockIndex: Int,
        charOffset: Int,
        anchorText: String,
        fraction: Float,
        positionAt: Long,
        positionSessionAt: Long,
        positionSource: String,
        chapterFinished: Boolean,
    ): Int

    @Query("DELETE FROM plugin_books WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}

/**
 * The user's settings for one stored story, library member or not. Each setter updates one
 * column, so writers never hold a copy of the others.
 */
@Entity(tableName = "plugin_stories")
data class PluginStoryEntity(
    @PrimaryKey val bookId: String,
    val titleOverride: String = "",
    val newChapters: Int = 0,
    val notify: Boolean? = null,
    val cacheLevel: Int,
    val cleanup: Boolean,
    val keepAll: Boolean = false,
)

data class StoryCount(val bookId: String, val newChapters: Int)

data class StoryTitle(val bookId: String, val titleOverride: String)

@Dao
interface PluginStoryDao {
    @Query("SELECT * FROM plugin_stories WHERE bookId = :bookId")
    suspend fun get(bookId: String): PluginStoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: PluginStoryEntity): Long

    @Query("UPDATE plugin_stories SET titleOverride = :title WHERE bookId = :bookId")
    suspend fun setTitleOverride(bookId: String, title: String)

    @Query("UPDATE plugin_stories SET notify = :on WHERE bookId = :bookId")
    suspend fun setNotify(bookId: String, on: Boolean)

    @Query("UPDATE plugin_stories SET cacheLevel = :cacheLevel, cleanup = :cleanup WHERE bookId = :bookId")
    suspend fun setCachePolicy(bookId: String, cacheLevel: Int, cleanup: Boolean)

    @Query("UPDATE plugin_stories SET keepAll = :on WHERE bookId = :bookId")
    suspend fun setKeepAll(bookId: String, on: Boolean)

    @Query("UPDATE plugin_stories SET newChapters = newChapters + :count WHERE bookId = :bookId")
    suspend fun addNewChapters(bookId: String, count: Int)

    @Query("UPDATE plugin_stories SET newChapters = 0 WHERE bookId = :bookId")
    suspend fun clearNewChapters(bookId: String)

    @Query("SELECT bookId, newChapters FROM plugin_stories WHERE newChapters > 0")
    fun newChapterCounts(): Flow<List<StoryCount>>

    @Query("SELECT bookId, titleOverride FROM plugin_stories WHERE titleOverride != ''")
    suspend fun renamed(): List<StoryTitle>

    @Query("DELETE FROM plugin_stories WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}

@Database(entities = [PluginBookEntity::class, PluginStoryEntity::class], version = 3, exportSchema = true)
abstract class PluginDatabase : RoomDatabase() {
    abstract fun books(): PluginBookDao
    abstract fun stories(): PluginStoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plugin_stories` (`bookId` TEXT NOT NULL, " +
                        "`titleOverride` TEXT NOT NULL, `newChapters` INTEGER NOT NULL, `notify` INTEGER, " +
                        "`cacheLevel` INTEGER NOT NULL, `cleanup` INTEGER NOT NULL, `keepAll` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`bookId`))",
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plugin_books` ADD COLUMN `chapterFinished` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

/** One open [PluginDatabase] per plugin id, created on first use. */
class PluginDatabases(private val context: Context) {
    private val open = HashMap<String, PluginDatabase>()

    fun get(pluginId: String): PluginDatabase = synchronized(open) {
        open.getOrPut(pluginId) {
            Room.databaseBuilder(context, PluginDatabase::class.java, fileName(pluginId))
                .addMigrations(PluginDatabase.MIGRATION_1_2, PluginDatabase.MIGRATION_2_3)
                .build()
        }
    }

    fun books(pluginId: String): PluginBookDao = get(pluginId).books()

    fun stories(pluginId: String): PluginStoryDao = get(pluginId).stories()

    /** Close and delete [pluginId]'s database (uninstall, Delete plugin data). */
    fun delete(pluginId: String) {
        synchronized(open) { open.remove(pluginId) }?.close()
        context.deleteDatabase(fileName(pluginId))
    }

    private fun fileName(pluginId: String): String =
        "plugin_${pluginId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.db"
}
