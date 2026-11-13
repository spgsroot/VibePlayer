package ru.spgsroot.peachy.data.notes

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** What a note is attached to. [ref] means a gallery id, a page URL or a video id respectively. */
object NoteKind {
    const val GALLERY = "gallery"
    const val PAGE = "page"
    const val VIDEO = "video"

    val ALL = setOf(GALLERY, PAGE, VIDEO)
}

/** One private free-text note per item. */
@Entity(tableName = "notes", primaryKeys = ["kind", "ref"])
data class NoteEntity(
    val kind: String,
    val ref: String,
    val text: String,
    val updatedAt: Long
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE kind = :kind AND ref = :ref")
    fun observe(kind: String, ref: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE kind = :kind AND ref = :ref")
    suspend fun delete(kind: String, ref: String)
}
