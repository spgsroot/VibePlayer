package ru.spgsroot.peachy.data.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.spgsroot.peachy.data.db.AppDatabase
import ru.spgsroot.peachy.data.repository.PhotoRepository

@RunWith(AndroidJUnit4::class)
class PhotoImporterTest {
    private lateinit var database: AppDatabase
    private lateinit var storage: PhotoStorageManager
    private lateinit var importer: PhotoImporter
    private lateinit var root: File

    @Before fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.cacheDir, "photo-import-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : android.content.ContextWrapper(base) {
            override fun getFilesDir(): File = root
        }
        database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        storage = PhotoStorageManager(context)
        importer = PhotoImporter(context, storage, database)
    }

    @After fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test fun actualSavedBytesSupplyDimensionsNotTheDisplayExtension() = runBlocking {
        val bytes = imageBytes(1200, 800)
        val result = importer.importSource("../../holiday.txt", bytes.size.toLong()) { ByteArrayInputStream(bytes) }
        assertTrue(result.isSuccess)
        val photo = database.photoDao().getAllNow().single()
        assertEquals("holiday", photo.title)
        assertEquals(1200, photo.width)
        assertEquals(800, photo.height)
        assertEquals(bytes.size.toLong(), photo.fileSize)
        assertArrayEquals(bytes, File(photo.filePath).readBytes())
        BitmapFactory.decodeFile(photo.thumbnailPath!!).let { thumbnail ->
            assertTrue(thumbnail.width <= 512 && thumbnail.height <= 512)
            thumbnail.recycle()
        }
    }

    @Test fun invalidImageLeavesNoRowOriginalOrThumbnail() = runBlocking {
        val result = importer.importSource("fake.jpg", 3) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        assertTrue(result.isFailure)
        assertEmpty()
    }

    @Test fun declaredOversizeLeavesNoRowOriginalOrThumbnail() = runBlocking {
        val result = importer.importSource("too-big.jpg", 100L * 1024 * 1024 + 1) {
            ByteArrayInputStream(imageBytes(20, 10))
        }
        assertTrue(result.isFailure)
        assertEmpty()
    }

    @Test fun cancelledProviderIsNotConvertedToSuccessOrFailureAndLeavesNoArtifacts() = runBlocking {
        try {
            importer.importSource(null, -1) { throw CancellationException("cancelled provider") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEmpty()
        }
    }

    @Test fun cancellationDuringCopyRemovesPartialOriginal() = runBlocking {
        val bytes = imageBytes(100, 80)
        val task = async {
            val job = currentCoroutineContext().job
            importer.importSource("cancelled.jpg", bytes.size.toLong()) {
                object : ByteArrayInputStream(bytes) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        val count = super.read(buffer, offset, length)
                        job.cancel()
                        return count
                    }
                }
            }
        }
        try {
            task.await()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEmpty()
    }

    @Test fun databaseInsertFailureRollsBackOriginalAndGeneratedThumbnail() = runBlocking {
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_photo_insert BEFORE INSERT ON photos " +
                    "BEGIN SELECT RAISE(ABORT, 'reject insert'); END"
            )
        }
        val bytes = imageBytes(100, 80)
        assertTrue(importer.importSource("photo.jpg", bytes.size.toLong()) { ByteArrayInputStream(bytes) }.isFailure)
        assertEmpty()
    }

    @Test fun nonContentUriIsRejectedWithoutOpeningOrSavingAnything() = runBlocking {
        assertTrue(importer.importUri(android.net.Uri.parse("file:///private/image.jpg")).isFailure)
        assertEmpty()
    }

    @Test fun generatedThumbnailAppliesExifRotation() = runBlocking {
        val source = File(root, "exif.jpg").apply { writeBytes(imageBytes(80, 40)) }
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        assertTrue(importer.importSource("rotated.jpg", source.length()) { source.inputStream() }.isSuccess)
        val photo = database.photoDao().getAllNow().single()
        val thumbnail = BitmapFactory.decodeFile(photo.thumbnailPath!!)
        try {
            assertEquals(40, thumbnail.width)
            assertEquals(80, thumbnail.height)
        } finally { thumbnail.recycle() }
    }

    @Test fun generatedThumbnailAppliesExifMirror() = runBlocking {
        val source = File(root, "mirrored.jpg")
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            for (y in 0 until 40) for (x in 40 until 80) bitmap.setPixel(x, y, Color.BLUE)
            source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally { bitmap.recycle() }
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString())
            saveAttributes()
        }
        assertTrue(importer.importSource("mirrored.jpg", source.length()) { source.inputStream() }.isSuccess)
        val thumbnail = BitmapFactory.decodeFile(database.photoDao().getAllNow().single().thumbnailPath!!)
        try {
            val left = thumbnail.getPixel(10, 20)
            val right = thumbnail.getPixel(70, 20)
            assertTrue(Color.blue(left) > Color.red(left))
            assertTrue(Color.red(right) > Color.blue(right))
        } finally { thumbnail.recycle() }
    }

    @Test fun deletionKeepsRowWhenAFileCannotBeDeletedAndCanBeRetried() = runBlocking {
        val bytes = imageBytes(20, 10)
        assertTrue(importer.importSource(null, bytes.size.toLong()) { ByteArrayInputStream(bytes) }.isSuccess)
        val photo = database.photoDao().getAllNow().single()
        val original = File(photo.filePath)
        original.delete()
        original.mkdir() // Not a file: scoped deletion must fail rather than silently deleting the row.
        File(original, "child").writeText("keep")
        val repository = PhotoRepository(database, storage)
        try {
            repository.delete(photo)
            fail("Deletion must report the failed original deletion")
        } catch (_: IOException) { }
        assertNotNull(database.photoDao().getById(photo.id))
        assertTrue(File(original, "child").exists())
        original.deleteRecursively()
        repository.delete(photo)
        assertNull(database.photoDao().getById(photo.id))
        assertFalse(File(photo.thumbnailPath!!).exists())
    }

    private suspend fun assertEmpty() {
        assertTrue(database.photoDao().getAllNow().isEmpty())
        assertTrue(File(root, "photos").listFiles().orEmpty().isEmpty())
        assertTrue(File(root, "photo_thumbnails").listFiles().orEmpty().isEmpty())
    }

    private fun imageBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.RED)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
