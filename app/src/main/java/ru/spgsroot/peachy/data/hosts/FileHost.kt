package ru.spgsroot.peachy.data.hosts

import okhttp3.HttpUrl

/** One file on a file host. [id] is whatever the host needs to resolve it; [name] is blank when unknown. */
data class HostFile(val id: String, val name: String, val size: Long? = null)

data class HostAlbum(val title: String, val files: List<HostFile>)

/** A fresh, expiring download URL plus the headers the CDN expects with it. */
data class DirectLink(val url: String, val name: String, val headers: Map<String, String>)

sealed interface HostLink {
    data class Album(val id: String) : HostLink
    data class File(val id: String) : HostLink
}

class HostException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Minimal blocking HTTP surface, so hosts can be tested without a network. */
interface Http {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String
    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String
}

interface FileHost {
    val name: String

    /** Recognises this host's album or file URLs; anything else is null. */
    fun parse(url: HttpUrl): HostLink?

    /** Lists an album. Files are not filtered; callers keep the videos. */
    fun album(id: String): HostAlbum

    /** A single-file link as a [HostFile], resolving whatever id the download step needs. */
    fun single(id: String): HostFile

    /** Resolves a file to a download URL. Links expire in minutes to hours, so call this right before downloading. */
    fun resolve(file: HostFile): DirectLink
}

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "ts", "3gp", "flv")

/** The player only handles these containers; images and archives on the same host are skipped. */
fun isVideoName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

internal fun String.unescapeHtml(): String = this
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&#x27;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")

/** Undoes the escapes of a JS string literal as these sites emit them (`\/`, `\'`, `\"`, `\\`). */
internal fun String.unescapeJs(): String {
    val out = StringBuilder(length)
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '\\' && i + 1 < length) {
            val next = this[i + 1]
            if (next == 'u' && i + 5 < length) {
                val code = substring(i + 2, i + 6).toIntOrNull(16)
                if (code != null) {
                    out.append(code.toChar())
                    i += 6
                    continue
                }
            }
            out.append(next)
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/**
 * Bunkr stores some names as UTF-8 that was decoded as Latin-1 and encoded again ("ChloÃ©"). When the text
 * is exactly such a double encoding it is repaired; any other text, including genuine accents, is returned as is.
 */
internal fun String.repairDoubleEncoding(): String {
    if (all { it.code < 0x80 } || any { it.code > 0xFF }) return this
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(toByteArray(Charsets.ISO_8859_1)))
            .toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        this
    }
}

internal fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

internal fun HttpUrl.hostMatches(vararg roots: String): Boolean =
    roots.any { root -> host == root || host.endsWith(".$root") }
