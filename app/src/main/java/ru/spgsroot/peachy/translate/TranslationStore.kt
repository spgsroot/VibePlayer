package ru.spgsroot.peachy.translate

import javax.inject.Inject
import javax.inject.Singleton
import ru.spgsroot.peachy.data.db.PageTranslationDao
import ru.spgsroot.peachy.data.db.PageTranslationEntity

/** Translations already paid for; reopening a page never asks the provider again. */
interface TranslationCache {
    suspend fun get(galleryId: Int, page: Int, lang: String): PageTranslation?
    suspend fun put(galleryId: Int, page: Int, lang: String, model: String, translation: PageTranslation)
    suspend fun cachedPages(galleryId: Int, lang: String): Set<Int>
}

/** The cache kept in the encrypted database. */
@Singleton
class TranslationStore @Inject constructor(private val dao: PageTranslationDao) : TranslationCache {
    /** A row that no longer parses counts as missing, so the page is simply translated again. */
    override suspend fun get(galleryId: Int, page: Int, lang: String): PageTranslation? =
        dao.get(galleryId, page, lang)?.let { PageTranslation.parse(it.json) }

    override suspend fun put(galleryId: Int, page: Int, lang: String, model: String, translation: PageTranslation) {
        dao.upsert(PageTranslationEntity(galleryId, page, lang, model, translation.toJson(), System.currentTimeMillis()))
    }

    override suspend fun cachedPages(galleryId: Int, lang: String): Set<Int> = dao.pagesDone(galleryId, lang).toSet()

    suspend fun deleteGallery(galleryId: Int) = dao.deleteGallery(galleryId)
}
