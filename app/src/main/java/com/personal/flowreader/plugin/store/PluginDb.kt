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
import com.personal.flowreader.data.ReadingPosition

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
            "positionAt = :positionAt, positionSessionAt = :positionSessionAt, positionSource = :positionSource " +
            "WHERE bookId = :bookId",
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
    ): Int

    @Query("DELETE FROM plugin_books WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}

@Database(entities = [PluginBookEntity::class], version = 1, exportSchema = true)
abstract class PluginDatabase : RoomDatabase() {
    abstract fun books(): PluginBookDao
}

/** One open [PluginDatabase] per plugin id, created on first use. */
class PluginDatabases(private val context: Context) {
    private val open = HashMap<String, PluginDatabase>()

    fun get(pluginId: String): PluginDatabase = synchronized(open) {
        open.getOrPut(pluginId) {
            Room.databaseBuilder(context, PluginDatabase::class.java, fileName(pluginId)).build()
        }
    }

    fun books(pluginId: String): PluginBookDao = get(pluginId).books()

    /** Close and delete [pluginId]'s database (uninstall, Delete plugin data). */
    fun delete(pluginId: String) {
        synchronized(open) { open.remove(pluginId) }?.close()
        context.deleteDatabase(fileName(pluginId))
    }

    private fun fileName(pluginId: String): String =
        "plugin_${pluginId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.db"
}
