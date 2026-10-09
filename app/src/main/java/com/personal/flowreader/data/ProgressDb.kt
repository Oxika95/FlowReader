package com.personal.flowreader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A book on the Files tab or a custom shelf. */
@Entity(tableName = "library_books")
data class LibraryBookEntity(
    /** SHA-256 of the file bytes. */
    @PrimaryKey val bookId: String,
    val title: String,
    val storedPath: String,
    /** Persisted SAF URI for Linked books; blank for Imported copies. */
    val sourceUri: String = "",
    /** [BookSource] name. */
    val sourceKind: String = BookSource.Imported.name,
    /** Custom shelf id; blank = Files tab. */
    val shelfId: String = "",
    val addedAt: Long,
    /** Local filter rules (JSON); blank = none. */
    val localFilters: String = "",
    @Embedded val position: ReadingPosition = ReadingPosition(),
)

/**
 * One Queue entry. Owns its file reference and its own reading position: two entries with the
 * same content, or an entry whose book is also in the library, never share a position.
 */
@Entity(
    tableName = "queue_items",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["sortOrder", "done"]),
    ],
)
data class QueueItemEntity(
    @PrimaryKey val queId: String,
    /** Content id: SHA-256 of the stored file, or a plugin book id (`rr:…`). */
    val bookId: String,
    val title: String,
    val storedPath: String,
    val sourceUri: String = "",
    /** [BookSource] name, or the plugin id for plugin stories. */
    val sourceKind: String = BookSource.Imported.name,
    /** Web page the entry was imported from; blank for text, files and plugin stories. */
    val sourceUrl: String = "",
    /** [QueueOrigin] name. */
    val origin: String = QueueOrigin.Text.name,
    val sortOrder: Int,
    val addedAt: Long,
    val done: Boolean = false,
    val doneAt: Long = 0L,
    val localFilters: String = "",
    @Embedded val position: ReadingPosition = ReadingPosition(),
)

/** How a Queue entry was added. */
enum class QueueOrigin { Text, Web, File, Library, Plugin }

/** The Queue as a whole (single row, [id] 0): the entry the stream was last reading. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentQueId: String = "",
    val updatedAt: Long = 0L,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_books WHERE bookId = :id")
    suspend fun get(id: String): LibraryBookEntity?

    @Query("SELECT * FROM library_books WHERE bookId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<LibraryBookEntity>

    @Query("SELECT * FROM library_books WHERE shelfId = :shelfId ORDER BY MAX(addedAt, positionAt) DESC")
    suspend fun shelf(shelfId: String): List<LibraryBookEntity>

    @Query("SELECT * FROM library_books ORDER BY MAX(addedAt, positionAt) DESC")
    suspend fun all(): List<LibraryBookEntity>

    @Query("UPDATE library_books SET shelfId = '' WHERE shelfId = :shelfId")
    suspend fun clearShelf(shelfId: String)

    @Query("SELECT COUNT(*) FROM library_books WHERE storedPath = :storedPath")
    suspend fun countForPath(storedPath: String): Int

    /** Returns -1 when the row already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: LibraryBookEntity): Long

    @Query(
        "UPDATE library_books SET title = :title, storedPath = :storedPath, sourceUri = :sourceUri, " +
            "sourceKind = :sourceKind, shelfId = :shelfId WHERE bookId = :id",
    )
    suspend fun updateFile(id: String, title: String, storedPath: String, sourceUri: String, sourceKind: String, shelfId: String)

    @Query("UPDATE library_books SET localFilters = :json WHERE bookId = :id")
    suspend fun setLocalFilters(id: String, json: String): Int

    @Query(
        "UPDATE library_books SET chapterIndex = :chapterIndex, chapterHref = :chapterHref, " +
            "blockIndex = :blockIndex, charOffset = :charOffset, anchorText = :anchorText, fraction = :fraction, " +
            "positionAt = :positionAt, positionSessionAt = :positionSessionAt, positionSource = :positionSource " +
            "WHERE bookId = :id",
    )
    suspend fun writePosition(
        id: String,
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

    @Query("DELETE FROM library_books WHERE bookId = :id")
    suspend fun delete(id: String)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun all(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE queId = :queId")
    suspend fun get(queId: String): QueueItemEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM queue_items")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM queue_items WHERE storedPath = :storedPath")
    suspend fun countForPath(storedPath: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: QueueItemEntity)

    @Query("DELETE FROM queue_items WHERE queId = :queId")
    suspend fun delete(queId: String)

    @Query("DELETE FROM queue_items WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("UPDATE queue_items SET sortOrder = :sortOrder WHERE queId = :queId")
    suspend fun setSortOrder(queId: String, sortOrder: Int)

    @Query("UPDATE queue_items SET done = 1, doneAt = :at WHERE queId = :queId AND done = 0")
    suspend fun markDone(queId: String, at: Long)

    @Query("UPDATE queue_items SET localFilters = :json WHERE queId = :queId")
    suspend fun setLocalFilters(queId: String, json: String): Int

    @Query(
        "UPDATE queue_items SET chapterIndex = :chapterIndex, chapterHref = :chapterHref, " +
            "blockIndex = :blockIndex, charOffset = :charOffset, anchorText = :anchorText, fraction = :fraction, " +
            "positionAt = :positionAt, positionSessionAt = :positionSessionAt, positionSource = :positionSource " +
            "WHERE queId = :queId",
    )
    suspend fun writePosition(
        queId: String,
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

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun state(): QueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: QueueStateEntity)
}

@Database(
    entities = [LibraryBookEntity::class, QueueItemEntity::class, QueueStateEntity::class],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun queue(): QueueDao
}
