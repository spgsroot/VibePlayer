package ru.spgsroot.peachy.data.browser

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.spgsroot.peachy.browser.adblock.FilterEngine

/**
 * Holds the current [FilterEngine]. Lists ship inside the app; the user can fetch newer copies,
 * which are stored in the app's files and take over from the bundled ones.
 */
@Singleton
class AdblockService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _engine = MutableStateFlow(FilterEngine.EMPTY)
    val engine: StateFlow<FilterEngine> = _engine.asStateFlow()

    private val lock = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val listsDir get() = File(context.filesDir, "filters").apply { mkdirs() }

    /** Builds the engine from the stored or bundled lists unless one is already loaded. */
    suspend fun ensureLoaded() {
        if (_engine.value !== FilterEngine.EMPTY) return
        lock.withLock {
            if (_engine.value !== FilterEngine.EMPTY) return
            _engine.value = withContext(Dispatchers.Default) { build() }
        }
    }

    /** Downloads every list again. Nothing is replaced unless all lists came down intact. */
    suspend fun update(): Result<Int> = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val fresh = SOURCES.map { source -> source.name to fetch(source) }
                fresh.forEach { (name, text) -> writeAtomically(File(listsDir, "$name.txt"), text) }
                val engine = build()
                _engine.value = engine
                engine.networkRuleCount + engine.cosmeticRuleCount
            }
        }
    }

    private fun build(): FilterEngine {
        val builder = FilterEngine.Builder()
        for (source in SOURCES) {
            val stored = File(listsDir, "${source.name}.txt")
            val text = if (stored.isFile) {
                stored.readText()
            } else {
                context.assets.open("filters/${source.name}.txt").bufferedReader().use { it.readText() }
            }
            builder.addList(text)
        }
        return builder.build()
    }

    private fun fetch(source: Source): String {
        val parts = source.urls.map { url ->
            val request = Request.Builder().url(url).header("User-Agent", "Peachy").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code} for $url")
                val body = response.body?.string().orEmpty()
                // A captive portal or error page answers 200 with HTML; a list starts with its header.
                if (!body.trimStart().startsWith("[Adblock") && !body.trimStart().startsWith("!")) {
                    throw java.io.IOException("Not a filter list: $url")
                }
                if (body.length < MIN_LIST_BYTES) throw java.io.IOException("List too short: $url")
                body.replace("\r\n", "\n")
            }
        }
        return source.header + parts.joinToString("\n")
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("Could not store ${target.name}")
        }
    }

    private class Source(val name: String, val urls: List<String>, val header: String = "")

    private companion object {
        const val MIN_LIST_BYTES = 1_000
        const val EASYLIST = "https://easylist.to/easylist/"

        val SOURCES = listOf(
            Source("easylist", listOf("${EASYLIST}easylist.txt")),
            Source("easyprivacy", listOf("${EASYLIST}easyprivacy.txt")),
            Source(
                "adult",
                listOf(
                    "https://raw.githubusercontent.com/easylist/easylist/master/easylist_adult/adult_thirdparty.txt",
                    "https://raw.githubusercontent.com/easylist/easylist/master/easylist_adult/adult_specific_block.txt",
                    "https://raw.githubusercontent.com/easylist/easylist/master/easylist_adult/adult_specific_hide.txt"
                ),
                header = "[Adblock Plus 2.0]\n"
            )
        )
    }
}
