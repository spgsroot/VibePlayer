package ru.spgsroot.peachy.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One manga page the user saved on its own. [fileName] is the stored copy inside the app. */
@Entity(tableName = "saved_pages", primaryKeys = ["galleryId", "page"])
data class SavedPageEntity(
    val galleryId: Int,
    val page: Int,
    val title: String,
    val fileName: String,
    val savedAt: Long
)

@Dao
interface SavedPageDao {
    @Query("SELECT * FROM saved_pages ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedPageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedPageEntity)

    @Query("DELETE FROM saved_pages WHERE galleryId = :galleryId AND page = :page")
    suspend fun delete(galleryId: Int, page: Int)

    @Query("SELECT * FROM saved_pages WHERE galleryId = :galleryId AND page = :page")
    suspend fun get(galleryId: Int, page: Int): SavedPageEntity?
}
