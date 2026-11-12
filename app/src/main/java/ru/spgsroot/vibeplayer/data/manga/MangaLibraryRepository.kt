package ru.spgsroot.vibeplayer.data.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.spgsroot.vibeplayer.data.db.LibraryGalleryDao
import ru.spgsroot.vibeplayer.data.db.LibraryGalleryEntity
import ru.spgsroot.vibeplayer.manga.GalleryDetail
import ru.spgsroot.vibeplayer.manga.LibraryGallery
import ru.spgsroot.vibeplayer.manga.MangaLibraryPolicy
import ru.spgsroot.vibeplayer.manga.NhentaiCdn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLibraryRepository @Inject constructor(
    private val dao: LibraryGalleryDao,
    private val api: NhentaiApi,
    private val files: MangaFiles
) {
    private val policy = MangaLibraryPolicy()

    fun observe(): Flow<List<LibraryGallery>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun save(detail: GalleryDetail, favorite: Boolean, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        withContext(Dispatchers.IO) {
            val paths = detail.pagePaths
            paths.forEachIndexed { index, path ->
                val url = NhentaiCdn.page(path) ?: return@forEachIndexed
                if (files.pageFile(detail.id, path)?.exists() != true) {
                    files.save(detail.id, path, api.download(url))
                }
                onProgress(index + 1, paths.size)
            }
            val thumb = NhentaiCdn.thumb(detail.thumbnailPath)
            if (thumb != null && files.pageFile(detail.id, detail.thumbnailPath)?.exists() != true) {
                runCatching { files.save(detail.id, detail.thumbnailPath, api.download(thumb)) }
            }
            dao.upsert(detail.toEntity(favorite))
        }
    }

    suspend fun setFavorite(detail: GalleryDetail, favorite: Boolean) {
        withContext(Dispatchers.IO) {
            val existing = dao.getById(detail.id)?.toDomain()
            val current = existing?.let { listOf(it) }.orEmpty()
            val next = if (favorite) {
                policy.favorite(current, detail.toLibrary(favorite = false)).single()
            } else {
                policy.unfavorite(current.ifEmpty { listOf(detail.toLibrary(false)) }, detail.id).single()
            }
            dao.upsert(next.toEntity())
            runCatching { api.setFavorite(detail.id, favorite) }
        }
    }

    suspend fun remove(galleryId: Int) {
        withContext(Dispatchers.IO) {
            dao.deleteById(galleryId)
            files.deleteGallery(galleryId)
        }
    }

    fun localPage(galleryId: Int, remotePath: String) = files.pageFile(galleryId, remotePath)
}

private fun LibraryGalleryEntity.toDomain() = LibraryGallery(
    id = id,
    title = title,
    thumbnailPath = thumbnailPath,
    pagePaths = pagePaths.split('\n').filter { it.isNotBlank() },
    favorite = favorite
)

private fun LibraryGallery.toEntity() = LibraryGalleryEntity(
    id = id,
    title = title,
    thumbnailPath = thumbnailPath,
    pagePaths = pagePaths.joinToString("\n"),
    favorite = favorite,
    addedAt = System.currentTimeMillis()
)

private fun GalleryDetail.toLibrary(favorite: Boolean) = LibraryGallery(
    id = id,
    title = title,
    thumbnailPath = thumbnailPath,
    pagePaths = pagePaths,
    favorite = favorite
)

private fun GalleryDetail.toEntity(favorite: Boolean) = toLibrary(favorite).toEntity()
