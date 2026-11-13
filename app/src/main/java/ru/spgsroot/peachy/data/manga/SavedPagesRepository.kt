package ru.spgsroot.peachy.data.manga

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.db.SavedPageDao
import ru.spgsroot.peachy.data.db.SavedPageEntity
import ru.spgsroot.peachy.manga.GalleryDetail
import ru.spgsroot.peachy.manga.NhentaiCdn
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pages the user keeps one by one. Each has its own copy in private storage, so removing the
 * gallery from the library never takes a saved page with it.
 */
@Singleton
class SavedPagesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: SavedPageDao,
    private val api: NhentaiApi,
    private val files: MangaFiles
) {
    private val dir = File(context.filesDir, "manga_pages")

    fun observe(): Flow<List<SavedPageEntity>> = dao.observeAll()

    fun fileOf(page: SavedPageEntity): File = File(dir, page.fileName)

    /** Keeps page [page] (0-based) of [detail]; uses the downloaded copy when the gallery is saved. */
    suspend fun save(detail: GalleryDetail, page: Int) {
        withContext(Dispatchers.IO) {
            val path = detail.pagePaths.getOrNull(page) ?: throw IOException("no such page")
            val ext = path.substringAfterLast('.', "jpg").lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "jpg" }
            val name = "${detail.id}_$page.$ext"
            dir.mkdirs()
            val target = File(dir, name)
            val local = files.pageFile(detail.id, path)?.takeIf { it.isFile }
            if (local != null) {
                local.copyTo(target, overwrite = true)
            } else {
                val url = NhentaiCdn.page(path) ?: throw IOException("bad page path")
                val bytes = api.download(url)
                val part = File(dir, "$name.part")
                try {
                    part.writeBytes(bytes)
                    if (!part.renameTo(target)) throw IOException("could not store the page")
                } finally {
                    part.delete()
                }
            }
            dao.upsert(SavedPageEntity(detail.id, page, detail.title, name, System.currentTimeMillis()))
        }
    }

    suspend fun remove(galleryId: Int, page: Int) {
        withContext(Dispatchers.IO) {
            val row = dao.get(galleryId, page)
            dao.delete(galleryId, page)
            row?.let { fileOf(it).delete() }
        }
    }
}
