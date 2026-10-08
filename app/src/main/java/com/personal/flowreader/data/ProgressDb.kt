package com.personal.flowreader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val title: String,
    val storedPath: String,
    val sourceUri: String = "",
    val sourceKind: String = BookSource.Imported.name,
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
    /** When true, the book appears on the Files tab. Que-only shares stay false. */
    val inLibrary: Boolean = true,
    /** 0–1 reading progress matching the eReader header bar (block index / last block). */
    val readingProgress: Float = 0f,
    /**
     * Custom library tab id when sorted into a user shelf. Blank = Files tab.
     * Ignored when [inLibrary] is false (Que-only).
     */
    val libraryTabId: String = "",
    /** Text at the locus, to re-find it if indices drift (see `LocusAnchor`). */
    val anchorText: String = "",
    /** Stable key of the locus chapter (EPUB entry path), checked before [chapterIndex]. */
    val chapterHref: String = "",
)

@Entity(tableName = "book_filters")
data class BookFiltersEntity(
    @PrimaryKey val bookId: String,
    val rulesJson: String,
)

@Entity(
    tableName = "que_items",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["sortOrder", "done"]),
    ],
)
data class QueItemEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val sortOrder: Int,
    val addedAt: Long,
    val done: Boolean = false,
)

/** Que row joined with its progress/title for the Que tab UI. */
data class QueEntry(
    val item: QueItemEntity,
    val progress: ProgressEntity,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE bookId = :id")
    suspend fun get(id: String): ProgressEntity?

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): ProgressEntity?

    @Query("SELECT * FROM progress WHERE inLibrary = 1 AND libraryTabId = '' ORDER BY updatedAt DESC")
    suspend fun library(): List<ProgressEntity>

    @Query(
        "SELECT * FROM progress WHERE inLibrary = 1 AND libraryTabId = :tabId ORDER BY updatedAt DESC",
    )
    suspend fun libraryTab(tabId: String): List<ProgressEntity>

    @Query("UPDATE progress SET libraryTabId = '' WHERE libraryTabId = :tabId")
    suspend fun clearLibraryTab(tabId: String)

    @Query(
        "SELECT * FROM progress WHERE sourceKind = :sourceKind ORDER BY updatedAt DESC",
    )
    suspend fun pluginLibrary(sourceKind: String): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE bookId IN (:ids)")
    suspend fun getMany(ids: List<String>): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ProgressEntity)

    @Query("DELETE FROM progress WHERE bookId = :id")
    suspend fun delete(id: String)
}

@Dao
interface BookFiltersDao {
    @Query("SELECT * FROM book_filters WHERE bookId = :id")
    suspend fun get(id: String): BookFiltersEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: BookFiltersEntity)

    @Query("DELETE FROM book_filters WHERE bookId = :id")
    suspend fun delete(id: String)
}

@Dao
interface QueDao {
    @Query("SELECT * FROM que_items ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun all(): List<QueItemEntity>

    @Query("SELECT * FROM que_items ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<QueItemEntity>>

    @Query("SELECT * FROM que_items WHERE id = :id")
    suspend fun get(id: String): QueItemEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM que_items")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM que_items WHERE bookId = :bookId")
    suspend fun countForBook(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: QueItemEntity)

    @Query("DELETE FROM que_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE que_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM que_items WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("SELECT * FROM que_items WHERE bookId = :bookId")
    suspend fun forBook(bookId: String): List<QueItemEntity>

    @Query(
        "SELECT * FROM que_items WHERE sortOrder > :afterOrder AND done = 0 " +
            "ORDER BY sortOrder ASC, addedAt ASC LIMIT 1",
    )
    suspend fun nextUndone(afterOrder: Int): QueItemEntity?
}

@Database(
    entities = [ProgressEntity::class, BookFiltersEntity::class, QueItemEntity::class],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
    abstract fun bookFilters(): BookFiltersDao
    abstract fun que(): QueDao
}
