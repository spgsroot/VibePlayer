package ru.spgsroot.peachy.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import ru.spgsroot.peachy.data.repository.AlbumRepository

@RunWith(AndroidJUnit4::class)
class AlbumRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: AlbumRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repository = AlbumRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun mixedMoveReplacesMembershipAndDeletingAlbumKeepsMedia() = runBlocking {
        val first = repository.create("First")
        val second = repository.create("Second")
        val video = db.videoDao().insert(VideoEntity(title = "video", filePath = "/v", duration = 1, thumbnailPath = null, addedAt = 1, fileSize = 1))
        val photo = db.photoDao().insert(PhotoEntity(title = "photo", filePath = "/p", thumbnailPath = null, addedAt = 1, fileSize = 1, width = 1, height = 1))
        repository.move(listOf(video), listOf(photo), first)
        repository.move(listOf(video), emptyList(), second)
        assertEquals(second, db.videoDao().getById(video)!!.albumId)
        assertEquals(first, db.photoDao().getById(photo)!!.albumId)
        repository.rename(first, "Renamed")
        assertEquals("Renamed", db.albumDao().getAllNow().first { it.id == first }.name)
        repository.delete(first)
        assertNull(db.photoDao().getById(photo)!!.albumId)
        assertEquals("/p", db.photoDao().getById(photo)!!.filePath)
        assertEquals(second, db.videoDao().getById(video)!!.albumId)
        repository.move(listOf(video), emptyList(), null)
        assertNull(db.videoDao().getById(video)!!.albumId)
    }

    @Test fun missingDestinationRollsBackBothMediaKinds() = runBlocking {
        val album = repository.create("Existing")
        val video = db.videoDao().insert(VideoEntity(title = "video", filePath = "/v", duration = 1, thumbnailPath = null, addedAt = 1, fileSize = 1, albumId = album))
        val photo = db.photoDao().insert(PhotoEntity(title = "photo", filePath = "/p", thumbnailPath = null, addedAt = 1, fileSize = 1, width = 1, height = 1, albumId = album))
        try { repository.move(listOf(video), listOf(photo), 999); fail("Missing album must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(album, db.videoDao().getById(video)!!.albumId)
        assertEquals(album, db.photoDao().getById(photo)!!.albumId)
    }

    @Test fun staleContentEditsCannotUndoMovesOrRestoreDeletedAlbums() = runBlocking {
        val first = repository.create("First")
        val second = repository.create("Second")
        val videoId = db.videoDao().insert(VideoEntity(title = "video", filePath = "/v", duration = 1, thumbnailPath = null, addedAt = 1, fileSize = 1, albumId = first))
        val photoId = db.photoDao().insert(PhotoEntity(title = "photo", filePath = "/p", thumbnailPath = null, addedAt = 1, fileSize = 1, width = 1, height = 1, albumId = first))
        val videos = ru.spgsroot.peachy.data.repository.VideoRepository(db.videoDao())
        val photos = ru.spgsroot.peachy.data.repository.PhotoRepository(db, ru.spgsroot.peachy.data.storage.PhotoStorageManager(InstrumentationRegistry.getInstrumentation().targetContext))
        val staleVideo = videos.getById(videoId)!!
        val stalePhoto = db.photoDao().getById(photoId)!!
        repository.move(listOf(videoId), listOf(photoId), second)
        videos.update(staleVideo.copy(thumbnailPath = "/new-cover"))
        photos.update(stalePhoto.copy(title = "renamed"))
        assertEquals(second, db.videoDao().getById(videoId)!!.albumId)
        assertEquals("/new-cover", db.videoDao().getById(videoId)!!.thumbnailPath)
        assertEquals(second, db.photoDao().getById(photoId)!!.albumId)
        assertEquals("renamed", db.photoDao().getById(photoId)!!.title)
        repository.delete(first)
        repository.delete(second)
        videos.update(staleVideo.copy(title = "video renamed"))
        photos.update(stalePhoto.copy(title = "photo renamed"))
        assertNull(db.videoDao().getById(videoId)!!.albumId)
        assertNull(db.photoDao().getById(photoId)!!.albumId)
    }
}
