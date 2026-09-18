package com.personal.flowreader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val title: String,
    val storedPath: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE bookId = :id")
    suspend fun get(id: String): ProgressEntity?

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ProgressEntity)
}

@Database(entities = [ProgressEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao
}
