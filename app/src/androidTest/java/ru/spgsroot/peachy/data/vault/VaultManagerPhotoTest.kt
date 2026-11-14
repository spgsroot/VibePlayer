package ru.spgsroot.peachy.data.vault

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.spgsroot.peachy.data.db.AppDatabase
import ru.spgsroot.peachy.data.db.PhotoEntity
import ru.spgsroot.peachy.data.db.VideoEntity
import ru.spgsroot.peachy.data.manga.MangaFiles
import ru.spgsroot.peachy.data.manga.TagPreferences
import ru.spgsroot.peachy.data.storage.InternalStorageManager
import ru.spgsroot.peachy.data.storage.PhotoStorageManager

@RunWith(AndroidJUnit4::class)
class VaultManagerPhotoTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var manager: VaultManager
    private lateinit var storage: PhotoStorageManager
    private val password = "photo vault password".toCharArray()
    private val original = ByteArray(1024) { (it % 251).toByte() }
    private val thumbnail = byteArrayOf(6, 7, 8, 9)

    @Before fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.cacheDir, "vault-photo-test-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        storage = PhotoStorageManager(context)
        manager = VaultManager(
            context, database, database.videoDao(), database.libraryGalleryDao(), database.readHistoryDao(),
            database.searchHistoryDao(), database.tagBlacklistDao(), database.galleryMemoryDao(), database.noteDao(),
            database.bookmarkDao(), database.browserHistoryDao(), database.allowlistDao(), TagPreferences(context),
            database.pageTranslationDao(), InternalStorageManager(context), MangaFiles(context), storage
        )
    }

    @After fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    private fun meta(id: Long = 7, title: String = "photo", hasThumbnail: Boolean = true) =
        VaultPhoto(id, title, 123, original.size.toLong(), 640, 480, "jpg", hasThumbnail)

    private fun data(photos: List<VaultPhoto>) = VaultData(
        photos.fold(0) { count, photo -> count + if (photo.hasThumbnail) 2 else 1 }, emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, photos = photos
    )

    private fun archive(metadata: VaultData, entries: (VaultArchiveWriter) -> Unit): File {
        val out = ByteArrayOutputStream()
        VaultArchiveWriter(VaultCrypto.encrypt(out, password, VaultCrypto.MIN_ITERATIONS)).use { writer ->
            writer.add("data.json", metadata.toJson().toByteArray(Charsets.UTF_8))
            entries(writer)
        }
        return File(root, "archive-${System.nanoTime()}.peachy").apply { writeBytes(out.toByteArray()) }
    }

    private fun VaultArchiveWriter.addPhoto(photo: VaultPhoto) {
        add(photo.entryName, original)
        if (photo.hasThumbnail) add(photo.thumbnailEntryName, thumbnail)
    }

    private suspend fun seedPhoto(title: String = "local", id: Long = 0): PhotoEntity {
        val file = storage.save(original.inputStream(), "photo.jpg", original.size.toLong())
        val entity = PhotoEntity(id, title, file.absolutePath, null, 456, original.size.toLong(), 640, 480)
        val assigned = database.photoDao().insert(entity)
        return entity.copy(id = assigned)
    }

    private suspend fun assertNoImportedPhotos() {
        assertTrue(database.photoDao().getAllNow().isEmpty())
        assertTrue(File(root, "photos").listFiles().orEmpty().isEmpty())
        assertTrue(File(root, "photo_thumbnails").listFiles().orEmpty().isEmpty())
    }

    private suspend fun expectFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (failure: Throwable) {
            return failure
        }
        throw AssertionError("Import should fail")
    }

    @Test fun exportImportRoundTripsActualPhotoBytesAndRemapsIdsWithoutTouchingVideo() = runBlocking {
        val source = seedPhoto("holiday", 7)
        val thumb = storage.saveThumbnail(thumbnail.inputStream(), source.id)
        database.photoDao().update(source.copy(thumbnailPath = thumb))
        val videoFile = File(root, "existing.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        database.videoDao().insert(VideoEntity(
            id = 7, title = "video", filePath = videoFile.absolutePath, duration = 12,
            thumbnailPath = null, addedAt = 4, fileSize = 3, isCorrupted = false
        ))
        val target = File(root, "export.peachy")
        val exportProgress = mutableListOf<Pair<Int, Int>>()
        val exported = manager.export(Uri.fromFile(target), password) { done, total -> exportProgress += done to total }
        assertEquals(1, exported.photos)
        assertEquals(1, exported.videos)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), exportProgress)
        database.photoDao().deleteById(source.id)
        storage.deleteFile(source.filePath)
        storage.deleteThumbnail(thumb)
        val local = seedPhoto("different local", 7) // Occupy the archived ID: the importer must allocate another.
        val importProgress = mutableListOf<Pair<Int, Int>>()
        val imported = manager.import(Uri.fromFile(target), password) { done, total -> importProgress += done to total }
        assertEquals(1, imported.photosAdded)
        assertEquals(0, imported.photosSkipped)
        assertEquals(0, imported.videosAdded)
        assertEquals(1, imported.videosSkipped)
        assertEquals(exportProgress, importProgress)
        val photo = database.photoDao().getAllNow().single { it.title == "holiday" }
        assertNotEquals(7L, photo.id)
        assertEquals(456L, photo.addedAt)
        assertEquals(640, photo.width)
        assertEquals(480, photo.height)
        assertEquals(original.size.toLong(), photo.fileSize)
        assertArrayEquals(original, File(photo.filePath).readBytes())
        assertArrayEquals(thumbnail, File(photo.thumbnailPath!!).readBytes())
        assertEquals(local, database.photoDao().getById(local.id))
        assertArrayEquals(byteArrayOf(1, 2, 3), videoFile.readBytes())
    }

    @Test fun localDuplicateKeepsItsFilesAndIncomingDuplicatesAreSkippedWithinOneVault() = runBlocking {
        val local = seedPhoto("photo")
        val a = meta()
        val b = meta(8)
        val c = meta(9, "new")
        val d = meta(10, "new")
        val file = archive(data(listOf(a, b, c, d))) { writer ->
            listOf(a, b, c, d).forEach { writer.addPhoto(it) }
        }
        val result = manager.import(Uri.fromFile(file), password)
        assertEquals(1, result.photosAdded)
        assertEquals(3, result.photosSkipped)
        assertEquals(local, database.photoDao().getById(local.id))
        assertArrayEquals(original, File(local.filePath).readBytes())
        assertEquals(2, File(root, "photos").listFiles()!!.size)
        assertEquals(1, File(root, "photo_thumbnails").listFiles()!!.size)
    }

    @Test fun differentDimensionsOrSizeOrTitleAreNotPhotoDuplicates() = runBlocking {
        seedPhoto("photo")
        val photos = listOf(
            meta(7, hasThumbnail = false).copy(width = 641),
            meta(8, hasThumbnail = false).copy(height = 481),
            meta(9, hasThumbnail = false).copy(fileSize = 3),
            meta(10, "Photo", false)
        )
        val file = archive(data(photos)) { writer ->
            photos.forEach { writer.add(it.entryName, if (it.id == 9L) byteArrayOf(1, 2, 3) else original) }
        }
        val result = manager.import(Uri.fromFile(file), password)
        assertEquals(4, result.photosAdded)
        assertEquals(0, result.photosSkipped)
        assertEquals(5, database.photoDao().getAllNow().size)
    }

    @Test fun archiveFailureAfterSavingOriginalAndThumbnailRollsBackBoth() = runBlocking {
        val photo = meta()
        val file = archive(data(listOf(photo)).copy(entryCount = 3)) { writer ->
            writer.addPhoto(photo)
            writer.add("padding", ByteArray(VaultCrypto.BLOCK_SIZE * 2))
        }
        val bytes = file.readBytes()
        bytes[bytes.size - 100] = (bytes[bytes.size - 100].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        val error = expectFailure { manager.import(Uri.fromFile(file), password) }
        assertTrue(error is VaultException)
        assertEquals(VaultError.CORRUPT, (error as VaultException).error)
        assertNoImportedPhotos()
    }

    @Test fun cancellationAfterThumbnailRollsBackRowsAndFiles() = runBlocking {
        val photo = meta()
        val file = archive(data(listOf(photo))) { it.addPhoto(photo) }
        supervisorScope {
            val task = async {
                val job = currentCoroutineContext().job
                manager.import(Uri.fromFile(file), password) { done, _ -> if (done == 2) job.cancel() }
            }
            assertTrue(expectFailure { task.await() } is CancellationException)
        }
        assertNoImportedPhotos()
    }

    @Test fun progressCallbackFailureRollsBackRowsAndFiles() = runBlocking {
        val photo = meta()
        val file = archive(data(listOf(photo))) { it.addPhoto(photo) }
        val failure = expectFailure {
            manager.import(Uri.fromFile(file), password) { done, _ -> if (done == 2) throw IOException("consumer failed") }
        }
        assertTrue(failure is IOException)
        assertNoImportedPhotos()
    }

    @Test fun databaseUpdateFailureRollsBackOriginalThumbnailAndRow() = runBlocking {
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_photo_update BEFORE UPDATE ON photos BEGIN SELECT RAISE(ABORT, 'reject'); END"
            )
        }
        val photo = meta()
        val file = archive(data(listOf(photo))) { it.addPhoto(photo) }
        expectFailure { manager.import(Uri.fromFile(file), password) }
        assertNoImportedPhotos()
    }

    @Test fun wrongPasswordAddsNothing() = runBlocking {
        val photo = meta()
        val file = archive(data(listOf(photo))) { it.addPhoto(photo) }
        val error = expectFailure { manager.import(Uri.fromFile(file), "wrong".toCharArray()) }
        assertEquals(VaultError.WRONG_PASSWORD, (error as VaultException).error)
        assertNoImportedPhotos()
    }

    @Test fun missingDuplicateUndeclaredAndSizeMismatchedPhotoEntriesAreCorrupt() = runBlocking {
        val photo = meta()
        val cases: List<(VaultArchiveWriter) -> Unit> = listOf(
            { it.add(photo.entryName, original) }, // Missing promised thumbnail.
            { it.add(photo.thumbnailEntryName, thumbnail) }, // Thumbnail before its original.
            { it.addPhoto(photo); it.add(photo.entryName, original) },
            { it.addPhoto(photo); it.add("photo/99.jpg", original) },
            { it.add(photo.entryName, byteArrayOf(1)); it.add(photo.thumbnailEntryName, thumbnail) }
        )
        for (entries in cases) {
            val file = archive(data(listOf(photo)), entries)
            val failure = expectFailure { manager.import(Uri.fromFile(file), password) }
            assertEquals(VaultError.CORRUPT, (failure as VaultException).error)
            assertNoImportedPhotos()
        }
    }

    @Test fun legacyVideoVaultIgnoresStaleProgressTotalAndHasNoPhotos() = runBlocking {
        val videoBytes = byteArrayOf(1, 2, 3)
        val metadata = data(emptyList()).copy(
            entryCount = -1,
            videos = listOf(VaultVideo(19, "legacy", 12, 123, 3, false, "mp4", false))
        )
        val json = org.json.JSONObject(metadata.toJson()).put("format", 1).apply { remove("photos") }
        val file = File(root, "legacy.peachy")
        VaultArchiveWriter(VaultCrypto.encrypt(file.outputStream(), password, VaultCrypto.MIN_ITERATIONS)).use {
            it.add("data.json", json.toString().toByteArray(Charsets.UTF_8))
            it.add("video/19.mp4", videoBytes)
        }
        val result = manager.import(Uri.fromFile(file), password)
        assertEquals(1, result.videosAdded)
        assertEquals(0, result.photosAdded)
        assertTrue(database.photoDao().getAllNow().isEmpty())
        val video = database.videoDao().getAllNow().single()
        assertEquals("legacy", video.title)
        assertArrayEquals(videoBytes, File(video.filePath).readBytes())
    }

    @Test fun photoExportRetainsTheTenCharacterStorageExtension() = runBlocking {
        val file = storage.save(original.inputStream(), "photo.abcdefghij")
        database.photoDao().insert(PhotoEntity(
            title = "extension", filePath = file.absolutePath, thumbnailPath = null,
            addedAt = 1, fileSize = original.size.toLong(), width = 640, height = 480
        ))
        val target = File(root, "extension.peachy")
        manager.export(Uri.fromFile(target), password)
        target.inputStream().use { input ->
            val reader = VaultArchiveReader(VaultCrypto.decrypt(input, password))
            val metadata = VaultData.fromJson(String(reader.next()!!.readBytes(100_000), Charsets.UTF_8))
            assertEquals("abcdefghij", metadata.photos.single().extension)
            assertEquals(metadata.photos.single().entryName, reader.next()!!.name)
            assertNull(reader.next())
        }
    }

    @Test fun albumIdsRemapWithoutMovingLocalDuplicateMedia() = runBlocking {
        val localAlbum = database.albumDao().insert(ru.spgsroot.peachy.data.db.AlbumEntity(7, "Local", 1))
        val local = seedPhoto("existing")
        database.albumDao().movePhotos(listOf(local.id), localAlbum)
        val photo = meta(title = "incoming")
        val duplicate = meta(8, "existing")
        val metadata = data(listOf(photo, duplicate)).copy(
            albums = listOf(ru.spgsroot.peachy.data.db.AlbumEntity(7, "Imported", 99)),
            photos = listOf(photo.copy(albumId = 7), duplicate.copy(albumId = 7))
        )
        val file = archive(metadata) { writer -> writer.addPhoto(photo); writer.addPhoto(duplicate) }
        manager.import(Uri.fromFile(file), password)
        val albums = database.albumDao().getAllNow()
        val importedAlbum = albums.single { it.name == "Imported" }
        assertNotEquals(localAlbum, importedAlbum.id)
        assertEquals(importedAlbum.id, database.photoDao().getAllNow().single { it.title == "incoming" }.albumId)
        assertEquals(localAlbum, database.photoDao().getById(local.id)!!.albumId)
    }

    @Test fun sameNameAlbumsRemainDistinctAndRepeatedImportDoesNotAddEmptyAlbums() = runBlocking {
        val first = meta(7, "first").copy(albumId = 4)
        val second = meta(8, "second").copy(albumId = 5)
        val metadata = data(listOf(first, second)).copy(albums = listOf(
            ru.spgsroot.peachy.data.db.AlbumEntity(4, "Same name", 99),
            ru.spgsroot.peachy.data.db.AlbumEntity(5, "Same name", 99)
        ))
        val file = archive(metadata) { writer -> writer.addPhoto(first); writer.addPhoto(second) }
        manager.import(Uri.fromFile(file), password)
        val photos = database.photoDao().getAllNow().associateBy { it.title }
        assertNotEquals(photos.getValue("first").albumId, photos.getValue("second").albumId)
        assertEquals(2, database.albumDao().getAllNow().size)
        manager.import(Uri.fromFile(file), password)
        assertEquals(2, database.albumDao().getAllNow().size)
        assertEquals(photos.getValue("first").albumId, database.photoDao().getById(photos.getValue("first").id)!!.albumId)
    }
}
