package ru.spgsroot.peachy.data.downloader

import android.content.SharedPreferences
import java.io.File
import java.io.OutputStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class DownloadMethod(val key: String) { InApp("in_app"), AnotherApp("another_app") }

data class DownloadOptions(
    val method: DownloadMethod = DownloadMethod.InApp,
    val folderUri: String? = null,
    val folderName: String? = null
)

/** Dialog preferences use stable keys, like the gallery layout preference. */
object DownloadOptionsStore {
    fun load(prefs: SharedPreferences) = DownloadOptions(
        method = DownloadMethod.entries.firstOrNull { it.key == prefs.getString("method", null) }
            ?: DownloadMethod.InApp,
        folderUri = prefs.getString("folder_uri", null),
        folderName = prefs.getString("folder_name", null)
    )

    fun save(prefs: SharedPreferences, options: DownloadOptions) {
        prefs.edit().putString("method", options.method.key)
            .putString("folder_uri", options.folderUri)
            .putString("folder_name", options.folderName).apply()
    }
}

/** Android-free request description so URL validation and outgoing payloads are testable. */
internal data class DownloadIntentRequest(
    val action: String,
    val data: String? = null,
    val mimeType: String? = null,
    val extras: Map<String, String> = emptyMap()
)

/** Shared by the dialog and outgoing intents, including OkHttp's valid-port constraints. */
internal fun isDownloadUrl(url: String): Boolean {
    val link = url.trim()
    return (link.startsWith("http://") || link.startsWith("https://")) && link.toHttpUrlOrNull() != null
}

internal fun externalDownloadRequests(url: String): List<DownloadIntentRequest> {
    val link = url.trim()
    if (!isDownloadUrl(link)) {
        return emptyList()
    }
    return listOf(
        DownloadIntentRequest("android.intent.action.VIEW", data = link),
        DownloadIntentRequest("android.intent.action.SEND", mimeType = "text/plain",
            extras = mapOf("android.intent.extra.TEXT" to link))
    )
}

/** A failed destination never deletes or modifies the already imported library file. */
internal fun copyDownloadedFile(source: File, openOutput: () -> OutputStream): Result<Unit> = runCatching {
    source.inputStream().use { input ->
        openOutput().use { output -> input.copyTo(output, bufferSize = 8192) }
    }
    Unit
}

/** Save errors stay errors; only a copy attempted after a successful save becomes a warning. */
internal suspend fun completeDownloadedVideo(
    source: File,
    save: suspend () -> Result<Unit>,
    copy: ((File) -> Unit)? = null
): Result<Int> = save().map {
    if (copy == null || runCatching { copy(source) }.isSuccess) 0 else 1
}
