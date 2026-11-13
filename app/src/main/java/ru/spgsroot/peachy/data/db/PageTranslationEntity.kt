package ru.spgsroot.peachy.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** What the model made of one page, in one language. [json] is `PageTranslation.toJson`; [model] says who wrote it. */
@Entity(tableName = "page_translations", primaryKeys = ["galleryId", "page", "lang"])
data class PageTranslationEntity(
    val galleryId: Int,
    val page: Int,
    val lang: String,
    val model: String,
    val json: String,
    val createdAt: Long
)

@Dao
interface PageTranslationDao {
    @Query("SELECT * FROM page_translations WHERE galleryId = :galleryId AND page = :page AND lang = :lang")
    suspend fun get(galleryId: Int, page: Int, lang: String): PageTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PageTranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PageTranslationEntity>)

    @Query("SELECT * FROM page_translations")
    suspend fun getAll(): List<PageTranslationEntity>

    @Query("SELECT page FROM page_translations WHERE galleryId = :galleryId AND lang = :lang")
    suspend fun pagesDone(galleryId: Int, lang: String): List<Int>

    @Query("DELETE FROM page_translations WHERE galleryId = :galleryId")
    suspend fun deleteGallery(galleryId: Int)
}
