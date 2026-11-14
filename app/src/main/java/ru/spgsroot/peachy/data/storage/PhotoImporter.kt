package ru.spgsroot.peachy.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.data.db.AppDatabase
import ru.spgsroot.peachy.data.db.PhotoEntity

@Singleton
class PhotoImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: PhotoStorageManager,
    private val database: AppDatabase
) {
    suspend fun importUri(uri: Uri): Result<Unit> {
        return try {
            require(uri.scheme == "content") { "Only content URIs can be imported" }
            val (name, size) = withContext(Dispatchers.IO) {
                var name: String? = null
                var size = -1L
                try {
                    context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                        }
                    }
                } catch (_: UnsupportedOperationException) {
                    // Metadata is optional; actual bytes are still size-limited and decoded below.
                }
                name to size
            }
            importSource(name, size) {
                context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open shared image")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    /** Owns the entire saved-byte operation, including rollback if dispatch back to the caller is cancelled. */
    internal suspend fun importSource(
        displayName: String?,
        totalBytes: Long,
        openSource: () -> InputStream
    ): Result<Unit> {
        var savedFile: File? = null
        var thumbnailPath: String? = null
        var insertedId: Long? = null
        try {
            return withContext(Dispatchers.IO) {
                require(totalBytes <= PhotoStorageManager.DEFAULT_MAX_PHOTO_BYTES) { "Photo is too large" }
                val jobContext = currentCoroutineContext()
                jobContext.ensureActive()
                // ContentProvider reads are blocking. Check cancellation between chunks as well as
                // before/after decoding; PhotoFiles removes a partial copy on any thrown failure.
                val source = object : FilterInputStream(openSource()) {
                    override fun read(): Int { jobContext.ensureActive(); return super.read() }
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        jobContext.ensureActive()
                        return super.read(bytes, offset, length)
                    }
                }
                val file = storage.save(source, displayName ?: "photo.img", totalBytes)
                savedFile = file
                jobContext.ensureActive()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                PhotoImportPolicy.requireImageBounds(bounds.outWidth, bounds.outHeight)
                thumbnailPath = createThumbnail(file, bounds.outWidth, bounds.outHeight)
                jobContext.ensureActive()
                val photo = PhotoEntity(
                    title = PhotoImportPolicy.title(displayName),
                    filePath = file.absolutePath,
                    thumbnailPath = thumbnailPath,
                    addedAt = System.currentTimeMillis(),
                    fileSize = file.length(),
                    width = bounds.outWidth,
                    height = bounds.outHeight
                )
                // Capture the ID even if cancellation arrives during Room's insert, so rollback
                // never leaves a committed row whose generated ID was lost at a suspension point.
                withContext(NonCancellable) { insertedId = database.photoDao().insert(photo) }
                jobContext.ensureActive()
                Result.success(Unit)
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                var removed = true
                thumbnailPath?.let {
                    if (!storage.deleteThumbnail(it)) {
                        removed = false
                        failure.addSuppressed(IOException("Cannot clean up photo thumbnail"))
                    }
                }
                savedFile?.let {
                    if (!storage.deleteFile(it.absolutePath)) {
                        removed = false
                        failure.addSuppressed(IOException("Cannot clean up photo file"))
                    }
                }
                // If deletion itself failed, retain a committed row so its files remain discoverable.
                if (removed) insertedId?.let { id ->
                    try { database.photoDao().deleteById(id) }
                    catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
                }
            }
            if (failure is CancellationException) throw failure
            if (failure is Error) throw failure
            return Result.failure(failure)
        }
    }

    private fun createThumbnail(file: File, width: Int, height: Int): String {
        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoImportPolicy.sampleSize(width, height)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IOException("Cannot decode shared image")
        var thumbnail = decoded
        try {
            val orientation = try {
                ExifInterface(file).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: IOException) {
                // BitmapFactory also supports formats without EXIF (for example BMP).
                ExifInterface.ORIENTATION_NORMAL
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (!matrix.isIdentity) {
                thumbnail = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
            val longest = maxOf(thumbnail.width, thumbnail.height)
            if (longest > PhotoImportPolicy.THUMBNAIL_SIZE) {
                val previous = thumbnail
                val scale = PhotoImportPolicy.THUMBNAIL_SIZE.toFloat() / longest
                thumbnail = Bitmap.createScaledBitmap(
                    previous, (previous.width * scale).toInt().coerceAtLeast(1),
                    (previous.height * scale).toInt().coerceAtLeast(1), true
                )
                if (previous !== decoded && previous !== thumbnail) previous.recycle()
            }
            return ByteArrayOutputStream().use { output ->
                check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "Cannot encode photo thumbnail" }
                storage.saveThumbnail(ByteArrayInputStream(output.toByteArray()), System.nanoTime())
            }
        } finally {
            if (thumbnail !== decoded) thumbnail.recycle()
            decoded.recycle()
        }
    }
}
