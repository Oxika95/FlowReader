package com.personal.flowreader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
)

@Entity(tableName = "book_filters")
data class BookFiltersEntity(
    @PrimaryKey val bookId: String,
    val rulesJson: String,
)

@Entity(tableName = "que_items")
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

    @Query("SELECT * FROM progress WHERE inLibrary = 1 ORDER BY updatedAt DESC")
    suspend fun library(): List<ProgressEntity>

    @Query(
        "SELECT * FROM progress WHERE sourceKind = :sourceKind ORDER BY updatedAt DESC",
    )
    suspend fun pluginLibrary(sourceKind: String): List<ProgressEntity>

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    suspend fun all(): List<ProgressEntity>

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

    @Query(
        "SELECT * FROM que_items WHERE sortOrder > :afterOrder AND done = 0 " +
            "ORDER BY sortOrder ASC, addedAt ASC LIMIT 1",
    )
    suspend fun nextUndone(afterOrder: Int): QueItemEntity?
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS book_filters (" +
                "bookId TEXT NOT NULL PRIMARY KEY, " +
                "rulesJson TEXT NOT NULL" +
                ")",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE progress ADD COLUMN sourceUri TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "ALTER TABLE progress ADD COLUMN sourceKind TEXT NOT NULL DEFAULT '${BookSource.Imported.name}'",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE progress ADD COLUMN inLibrary INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS que_items (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "bookId TEXT NOT NULL, " +
                "sortOrder INTEGER NOT NULL, " +
                "addedAt INTEGER NOT NULL, " +
                "done INTEGER NOT NULL DEFAULT 0" +
                ")",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE progress ADD COLUMN readingProgress REAL NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [ProgressEntity::class, BookFiltersEntity::class, QueItemEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
    abstract fun bookFilters(): BookFiltersDao
    abstract fun que(): QueDao
}
