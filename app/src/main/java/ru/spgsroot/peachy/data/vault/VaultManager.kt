package ru.spgsroot.peachy.data.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.db.AllowlistDao
import ru.spgsroot.peachy.data.db.AllowlistEntity
import ru.spgsroot.peachy.data.db.AppDatabase
import ru.spgsroot.peachy.data.db.BookmarkDao
import ru.spgsroot.peachy.data.db.BrowserHistoryDao
import ru.spgsroot.peachy.data.db.GalleryMemoryDao
import ru.spgsroot.peachy.data.db.LibraryGalleryDao
import ru.spgsroot.peachy.data.db.ReadHistoryDao
import ru.spgsroot.peachy.data.db.SearchHistoryDao
import ru.spgsroot.peachy.data.db.TagBlacklistDao
import ru.spgsroot.peachy.data.db.VideoDao
import ru.spgsroot.peachy.data.db.VideoEntity
import ru.spgsroot.peachy.data.manga.MangaFiles
import ru.spgsroot.peachy.data.manga.TagPreferences
import ru.spgsroot.peachy.data.notes.NoteDao
import ru.spgsroot.peachy.data.notes.NoteKind
import ru.spgsroot.peachy.data.notes.NoteRepository
import ru.spgsroot.peachy.data.storage.InternalStorageManager
import ru.spgsroot.peachy.manga.TagLibrary
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class VaultExportResult(val videos: Int, val galleries: Int, val notes: Int)

data class VaultImportResult(val videosAdded: Int, val videosSkipped: Int, val galleries: Int, val notes: Int)

/**
 * Writes the user's whole library to a password-protected `.peachy` file and merges one back in:
 * videos with thumbnails, saved galleries with their pages, read and search history, tag lists and
 * blacklist, notes, bookmarks, browser history and blocker exceptions. The PIN, the device address
 * and playback settings stay on the device.
 */
@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val videoDao: VideoDao,
    private val libraryDao: LibraryGalleryDao,
    private val readHistoryDao: ReadHistoryDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val tagBlacklistDao: TagBlacklistDao,
    private val memoryDao: GalleryMemoryDao,
    private val noteDao: NoteDao,
    private val bookmarkDao: BookmarkDao,
    private val browserHistoryDao: BrowserHistoryDao,
    private val allowlistDao: AllowlistDao,
    private val tagPreferences: TagPreferences,
    private val storage: InternalStorageManager,
    private val mangaFiles: MangaFiles
) {
    /**
     * Writes the vault to [target], a document the user created. [onProgress] gets (done, total)
     * file entries. A vault that fails halfway is deleted rather than left looking complete.
     */
    suspend fun export(
        target: Uri,
        password: CharArray,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): VaultExportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val videos = videoDao.getAllNow()
                .mapNotNull { entity -> File(entity.filePath).takeIf { it.isFile }?.let { entity to it } }
            val thumbnails = videos.associate { (entity, _) ->
                entity.id to entity.thumbnailPath?.let(::File)?.takeIf { it.isFile }
            }
            val galleries = libraryDao.getAll()
            val pages = galleries.associate { it.id to mangaFiles.listFiles(it.id).filter { f -> VaultArchive.isSafeName(f.name) } }
            val notes = noteDao.getAll()
            val entryCount = videos.size + thumbnails.values.count { it != null } + pages.values.sumOf { it.size }

            val data = VaultData(
                entryCount = entryCount,
                videos = videos.map { (entity, file) ->
                    VaultVideo(
                        entity.id, entity.title, entity.duration, entity.addedAt, file.length(), entity.isCorrupted,
                        extensionOf(file), thumbnails[entity.id] != null
                    )
                },
                galleries = galleries,
                readHistory = readHistoryDao.getAll(),
                searches = searchHistoryDao.getAll(),
                blacklist = tagBlacklistDao.getAll(),
                memory = memoryDao.getAll(),
                notes = notes,
                bookmarks = bookmarkDao.getAll(),
                browserHistory = browserHistoryDao.getAll(),
                allowlist = allowlistDao.getHosts(),
                tagLibrary = tagPreferences.snapshot().toJson()
            )

            val raw = resolver.openOutputStream(target, "wt") ?: throw IOException("Could not open the file")
            VaultArchiveWriter(VaultCrypto.encrypt(BufferedOutputStream(raw), password)).use { writer ->
                writer.add(DATA_ENTRY, data.toJson().toByteArray(Charsets.UTF_8))
                var done = 0
                fun step() {
                    onProgress(++done, entryCount)
                }
                for ((index, pair) in videos.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val (_, file) = pair
                    val video = data.videos[index]
                    file.inputStream().use { writer.add(video.entryName, video.fileSize, it) }
                    step()
                    thumbnails[video.id]?.let { thumb ->
                        thumb.inputStream().use { writer.add(video.thumbnailEntryName, thumb.length(), it) }
                        step()
                    }
                }
                for ((galleryId, files) in pages) {
                    for (file in files) {
                        currentCoroutineContext().ensureActive()
                        file.inputStream().use { writer.add("manga/$galleryId/${file.name}", file.length(), it) }
                        step()
                    }
                }
            }
            VaultExportResult(videos.size, galleries.size, notes.size)
        } catch (e: Throwable) {
            // A half-written vault would look like a real one; remove it.
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
    }

    /**
     * Merges the vault at [source] into the library. Nothing local is overwritten or removed; a
     * video already present (same title, length and size) is skipped. If the vault turns out to be
     * damaged, the videos and pages it already added are taken out again.
     */
    suspend fun import(
        source: Uri,
        password: CharArray,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): VaultImportResult = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(source) ?: throw IOException("Could not open the file")
        val createdVideos = mutableListOf<Long>()
        val createdFiles = mutableListOf<File>()
        try {
            raw.use { rawIn ->
                val reader = VaultArchiveReader(VaultCrypto.decrypt(BufferedInputStream(rawIn), password))
                val first = reader.next()
                if (first == null || first.name != DATA_ENTRY) throw VaultException(VaultError.CORRUPT, "Vault has no data document")
                val data = VaultData.fromJson(String(first.readBytes(MAX_DATA_BYTES), Charsets.UTF_8))

                val videoMeta = data.videos.associateBy { it.id }
                val known = videoDao.getAllNow().associateBy { VaultMerge.videoKey(it.title, it.duration, it.fileSize) }
                val idMap = HashMap<Long, Long>()
                val added = HashMap<Long, VideoEntity>()
                var skipped = 0
                var done = 0
                val pageGalleries = data.galleries.map { it.id }.toSet()

                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = reader.next() ?: break
                    val name = entry.name
                    when {
                        name.startsWith("video/") -> {
                            val meta = videoMeta[videoIdOf(name)]
                            if (meta != null && name == meta.entryName) {
                                val existing = known[VaultMerge.videoKey(meta.title, meta.duration, meta.fileSize)]
                                if (existing != null) {
                                    idMap[meta.id] = existing.id
                                    skipped++
                                } else {
                                    val file = storage.save(
                                        entry.stream, "vault_${System.nanoTime()}.${meta.extension}",
                                        totalBytes = entry.size, maxBytes = Long.MAX_VALUE
                                    )
                                    createdFiles += file
                                    val entity = VideoEntity(
                                        title = meta.title, filePath = file.absolutePath, duration = meta.duration,
                                        thumbnailPath = null, addedAt = meta.addedAt, fileSize = file.length(),
                                        isCorrupted = meta.isCorrupted
                                    )
                                    val id = videoDao.insert(entity)
                                    createdVideos += id
                                    idMap[meta.id] = id
                                    added[meta.id] = entity.copy(id = id)
                                }
                            }
                        }
                        name.startsWith("thumb/") -> {
                            val oldId = name.removePrefix("thumb/").toLongOrNull()
                            val entity = oldId?.let(added::get)
                            if (entity != null && videoMeta[oldId]?.hasThumbnail == true) {
                                val path = storage.saveThumbnail(entry.stream, entity.id)
                                createdFiles += File(path)
                                val updated = entity.copy(thumbnailPath = path)
                                videoDao.update(updated)
                                added[oldId] = updated
                            }
                        }
                        name.startsWith("manga/") -> {
                            val parts = name.removePrefix("manga/").split('/')
                            val galleryId = parts.firstOrNull()?.toIntOrNull()
                            if (parts.size == 2 && galleryId != null && galleryId in pageGalleries) {
                                mangaFiles.restore(galleryId, parts[1], entry.stream)?.let { createdFiles += it }
                            }
                        }
                    }
                    onProgress(++done, data.entryCount)
                }

                // The stream has been authenticated to its end; only now touch the rest of the database.
                applyData(data, idMap)
                VaultImportResult(
                    videosAdded = added.size,
                    videosSkipped = skipped,
                    galleries = data.galleries.size,
                    notes = data.notes.size
                )
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                createdVideos.forEach { runCatching { videoDao.deleteById(it) } }
                createdFiles.forEach { it.delete() }
            }
            throw e
        }
    }

    private suspend fun applyData(data: VaultData, idMap: Map<Long, Long>) {
        val notes = data.notes
            .filter { it.kind in NoteKind.ALL }
            .map { it.copy(text = it.text.take(NoteRepository.MAX_LENGTH)) }
            .mapNotNull { note ->
                if (note.kind != NoteKind.VIDEO) return@mapNotNull note
                val newId = note.ref.toLongOrNull()?.let(idMap::get) ?: return@mapNotNull null
                note.copy(ref = newId.toString())
            }

        database.withTransaction {
            val haveGalleries = libraryDao.getAll().associateBy { it.id }
            for (gallery in data.galleries) {
                val have = haveGalleries[gallery.id]
                if (have == null) libraryDao.upsert(gallery)
                else if (gallery.favorite && !have.favorite) libraryDao.upsert(have.copy(favorite = true))
            }
            VaultMerge.readHistoryToWrite(readHistoryDao.getAll(), data.readHistory).forEach { readHistoryDao.upsert(it) }
            val haveMemory = memoryDao.getAll().map { it.galleryId }.toSet()
            data.memory.filter { it.galleryId !in haveMemory }.forEach { memoryDao.upsert(it) }

            val searches = VaultMerge.searches(searchHistoryDao.getAll(), data.searches)
            searchHistoryDao.clear()
            searchHistoryDao.insertAll(searches)
            data.blacklist.forEach { tagBlacklistDao.upsert(it) }

            val current = noteDao.getAll()
            val merged = VaultMerge.notes(current, notes)
            val changed = merged.filter { it !in current }
            if (changed.isNotEmpty()) noteDao.upsertAll(changed)

            val haveBookmarks = bookmarkDao.getAll().map { it.url }.toSet()
            bookmarkDao.upsertAll(data.bookmarks.filter { it.url !in haveBookmarks })
            val haveVisits = browserHistoryDao.getAll().map { it.url }.toSet()
            browserHistoryDao.upsertAll(data.browserHistory.filter { it.url !in haveVisits })
            browserHistoryDao.trim(BrowserHistoryDao.MAX_ROWS)
            data.allowlist.forEach { allowlistDao.add(AllowlistEntity(it)) }
        }
        data.tagLibrary?.let { raw ->
            val incoming = TagLibrary.fromJson(raw)
            tagPreferences.update { VaultMerge.tagLibrary(it, incoming) }
        }
    }

    private fun videoIdOf(entryName: String): Long? =
        entryName.removePrefix("video/").substringBefore('.').toLongOrNull()

    private fun extensionOf(file: File): String =
        file.extension.lowercase().filter { it.isLetterOrDigit() }.take(8).ifEmpty { "mp4" }

    companion object {
        const val DATA_ENTRY = "data.json"
        const val FILE_EXTENSION = "peachy"
        private const val MAX_DATA_BYTES = 64 * 1024 * 1024
    }
}
