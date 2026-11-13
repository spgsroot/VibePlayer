package ru.spgsroot.peachy.translate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.spgsroot.peachy.data.manga.MangaLibraryRepository
import ru.spgsroot.peachy.manga.NhentaiCdn

/** Where the translator gets a page as a ready-to-send JPEG. */
interface PageSource {
    /** Throws [IOException] when the page cannot be fetched and [IllegalArgumentException] when it is not an image. */
    suspend fun jpeg(galleryId: Int, page: Int): ByteArray
}

/** Shrinks a page so the request stays small: the model reads text fine at 1600 px on the long side. */
object PageImage {
    const val MAX_SIDE = 1600
    private const val JPEG_QUALITY = 80

    /** Never upscales; keeps the aspect ratio. */
    fun scaledSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val longSide = max(width, height)
        if (longSide <= maxSide) return width to height
        val factor = maxSide.toDouble() / longSide
        return (width * factor).roundToInt().coerceAtLeast(1) to (height * factor).roundToInt().coerceAtLeast(1)
    }

    /** The largest power of two that still decodes at least [maxSide] on the long side. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        val longSide = max(width, height)
        var sample = 1
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    fun encode(source: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, options)
            ?: throw IllegalArgumentException("Not an image")
        val (width, height) = scaledSize(decoded.width, decoded.height)
        val scaled = if (width == decoded.width && height == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
        }
        return try {
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        } finally {
            scaled.recycle()
        }
    }
}

/** Reads a page from the saved copy when there is one, otherwise from the CDN. Bind it to a gallery's page paths first. */
class PageBytes @Inject constructor(private val library: MangaLibraryRepository) : PageSource {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var paths: List<String> = emptyList()

    fun bind(pagePaths: List<String>) {
        paths = pagePaths
    }

    override suspend fun jpeg(galleryId: Int, page: Int): ByteArray = withContext(Dispatchers.IO) {
        val path = paths.getOrNull(page) ?: throw IllegalArgumentException("No page $page")
        val local = library.localPage(galleryId, path)?.takeIf { it.isFile }
        val bytes = local?.readBytes() ?: download(path)
        PageImage.encode(bytes)
    }

    private fun download(path: String): ByteArray {
        val url = NhentaiCdn.page(path) ?: throw IllegalArgumentException("Bad page path")
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.bytes() ?: throw IOException("Empty body")
        }
    }
}
