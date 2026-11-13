package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A video found behind a host link, ready to be resolved and downloaded. */
data class HostEntry(val host: FileHost, val file: HostFile, val label: String)

/** Turns album and file links of the supported hosts into downloadable videos. */
@Singleton
class HostImporter(private val hosts: List<FileHost>, private val findr: FindrIndex) {
    @Inject
    constructor() : this(OkHttpHttp())

    private constructor(http: Http) : this(defaultHosts(http), FindrIndex(http))

    /** True when [url] is a link one of the supported hosts, or an album page of findr.to, owns. */
    fun recognizes(url: String): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        return findr.recognizes(parsed) || match(url) != null
    }

    /**
     * The videos behind [url]: every video of an album, or the one file of a file link. Images and archives
     * are skipped. Fails when the album has no videos.
     */
    fun expand(url: String): List<HostEntry> {
        val parsed = url.trim().toHttpUrlOrNull()
        // findr.to only points at an album on one of the hosts; the album itself is imported from there.
        if (parsed != null && findr.recognizes(parsed)) return expandHosted(findr.sourceOf(parsed))
        return expandHosted(url)
    }

    private fun expandHosted(url: String): List<HostEntry> {
        val (host, link) = match(url) ?: throw HostException("Unsupported link")
        return when (link) {
            is HostLink.File -> listOf(host.single(link.id).let { HostEntry(host, it, it.name.ifBlank { url }) })
            is HostLink.Album -> {
                val album = host.album(link.id)
                val videos = album.files.filter { isVideoName(it.name) }
                if (videos.isEmpty()) throw HostException("${host.name}: album \"${album.title}\" has no videos")
                videos.map { HostEntry(host, it, it.name) }
            }
        }
    }

    fun resolve(entry: HostEntry): DirectLink {
        val link = entry.host.resolve(entry.file)
        if (!isVideoName(link.name)) throw HostException("${entry.host.name}: \"${link.name}\" is not a video")
        return link
    }

    private fun match(url: String): Pair<FileHost, HostLink>? {
        val parsed = url.trim().toHttpUrlOrNull() ?: return null
        for (host in hosts) {
            val link = host.parse(parsed) ?: continue
            return host to link
        }
        return null
    }

    companion object {
        fun withHttp(http: Http): HostImporter = HostImporter(defaultHosts(http), FindrIndex(http))

        fun defaultHosts(http: Http): List<FileHost> = listOf(
            BunkrHost(http),
            CyberdropHost(http),
            TurboHost(http),
            PixeldrainHost(http),
            FilesterHost(http)
        )
    }
}

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

class OkHttpHttp : Http {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun get(url: String, headers: Map<String, String>): String =
        execute(Request.Builder().url(url).get(), headers)

    override fun postJson(url: String, json: String, headers: Map<String, String>): String =
        execute(Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())), headers)

    private fun execute(builder: Request.Builder, headers: Map<String, String>): String {
        builder.header("User-Agent", BROWSER_USER_AGENT)
        headers.forEach { (name, value) -> builder.header(name, value) }
        try {
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw HostException("The host is rate limiting requests, try again in a minute")
                if (!response.isSuccessful) throw HostException("HTTP ${response.code} from ${response.request.url.host}")
                return response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw HostException("Could not reach ${builder.build().url.host}: ${e.message}", e)
        }
    }
}
