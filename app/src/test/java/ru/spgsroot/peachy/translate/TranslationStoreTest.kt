package ru.spgsroot.peachy.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.spgsroot.peachy.data.db.PageTranslationDao
import ru.spgsroot.peachy.data.db.PageTranslationEntity

class TranslationStoreTest {
    private val dao = MapDao()
    private val store = TranslationStore(dao)

    private fun page(text: String) =
        PageTranslation("ja", listOf(TranslatedLine(LineKind.Speech, "ハ", text)))

    @Test
    fun putThenGetRoundTrips() = runBlocking {
        store.put(1, 3, "ru", "m", page("А"))
        assertEquals(page("А"), store.get(1, 3, "ru"))
    }

    @Test
    fun otherLanguageIsAMiss() = runBlocking {
        store.put(1, 3, "ru", "m", page("А"))
        assertNull(store.get(1, 3, "en"))
        assertNull(store.get(1, 4, "ru"))
        assertNull(store.get(2, 3, "ru"))
    }

    @Test
    fun putReplacesSameKey() = runBlocking {
        store.put(1, 3, "ru", "m", page("А"))
        store.put(1, 3, "ru", "m2", page("Б"))
        assertEquals(page("Б"), store.get(1, 3, "ru"))
        assertEquals(1, dao.rows.size)
        assertEquals("m2", dao.rows.values.single().model)
    }

    @Test
    fun cachedPagesListsOnlyThatGalleryAndLanguage() = runBlocking {
        store.put(1, 0, "ru", "m", page("а"))
        store.put(1, 5, "ru", "m", page("б"))
        store.put(1, 7, "en", "m", page("c"))
        store.put(2, 9, "ru", "m", page("д"))
        assertEquals(setOf(0, 5), store.cachedPages(1, "ru"))
    }

    @Test
    fun unparseableStoredJsonIsAMiss() = runBlocking {
        dao.upsert(PageTranslationEntity(1, 3, "ru", "m", "{", 0L))
        assertNull(store.get(1, 3, "ru"))
    }

    @Test
    fun deleteGalleryRemovesAllLanguages() = runBlocking {
        store.put(1, 0, "ru", "m", page("а"))
        store.put(1, 0, "en", "m", page("a"))
        store.put(2, 0, "ru", "m", page("б"))
        store.deleteGallery(1)
        assertNull(store.get(1, 0, "ru"))
        assertNull(store.get(1, 0, "en"))
        assertEquals(page("б"), store.get(2, 0, "ru"))
    }

    private class MapDao : PageTranslationDao {
        val rows = LinkedHashMap<Triple<Int, Int, String>, PageTranslationEntity>()

        override suspend fun get(galleryId: Int, page: Int, lang: String) = rows[Triple(galleryId, page, lang)]
        override suspend fun upsert(entity: PageTranslationEntity) {
            rows[Triple(entity.galleryId, entity.page, entity.lang)] = entity
        }

        override suspend fun upsertAll(entities: List<PageTranslationEntity>) = entities.forEach { upsert(it) }
        override suspend fun getAll() = rows.values.toList()
        override suspend fun pagesDone(galleryId: Int, lang: String) =
            rows.values.filter { it.galleryId == galleryId && it.lang == lang }.map { it.page }

        override suspend fun deleteGallery(galleryId: Int) {
            rows.keys.removeAll { it.first == galleryId }
        }
    }
}
