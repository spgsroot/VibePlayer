package ru.spgsroot.peachy.data.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** One video to copy out of the app: [title] names the copy, [path] is the private file. */
data class ExportItem(val title: String, val path: String)

data class ExportResult(val exported: Int, val failed: List<String>)

/**
 * Copies videos from private storage into a folder the user picked with the system folder
 * chooser. Nothing is written until the user chooses the folder, and the app gets no storage
 * permission: the grant covers only that tree.
 */
@Singleton
class VideoExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Calls [onProgress] after each file with (done, total). Failures do not stop the rest. */
    fun export(
        treeUri: Uri,
        items: List<ExportItem>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ExportResult {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val failed = mutableListOf<String>()
        var exported = 0
        items.forEachIndexed { index, item ->
            try {
                copyOne(resolver, parent, item)
                exported++
            } catch (e: IOException) {
                failed += item.title
            } catch (e: SecurityException) {
                failed += item.title
            } catch (e: IllegalArgumentException) {
                failed += item.title
            }
            onProgress(index + 1, items.size)
        }
        return ExportResult(exported, failed)
    }

    private fun copyOne(resolver: ContentResolver, parent: Uri, item: ExportItem) {
        val source = File(item.path)
        if (!source.isFile) throw IOException("missing source")
        val name = exportFileName(item.title, source.extension)
        val target = DocumentsContract.createDocument(resolver, parent, mimeFor(source.extension), name)
            ?: throw IOException("could not create $name")
        try {
            val out = resolver.openOutputStream(target) ?: throw IOException("could not open $name")
            out.use { sink -> source.inputStream().use { it.copyTo(sink) } }
        } catch (e: IOException) {
            // A half-written copy would look like a real video; remove it.
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
    }
}

private const val MAX_NAME_CHARS = 120

/**
 * A file name safe for any provider: separators, control characters and the characters Windows
 * and FAT forbid become spaces, dots at the edges go, and the length is bounded. The extension
 * comes from the stored file, never from the title.
 */
fun exportFileName(title: String, extension: String): String {
    val ext = extension.lowercase().filter { it.isLetterOrDigit() }.take(8).ifEmpty { "mp4" }
    var base = title
        .map { if (it.isISOControl() || it in "\\/:*?\"<>|") ' ' else it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.')
    base = base.take(MAX_NAME_CHARS).trim(' ', '.')
    if (base.isEmpty()) base = "video"
    return "$base.$ext"
}

internal fun mimeFor(extension: String): String = when (extension.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    "avi" -> "video/x-msvideo"
    "3gp" -> "video/3gpp"
    else -> "application/octet-stream"
}
