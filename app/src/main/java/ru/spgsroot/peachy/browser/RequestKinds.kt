package ru.spgsroot.peachy.browser

import ru.spgsroot.peachy.browser.adblock.ResourceType

/**
 * WebView does not say what a sub-request is for, so the kind is guessed from the headers the
 * browser sent and from the file extension. A wrong guess only changes which type-limited
 * rules apply; it never blocks the page itself.
 */
object RequestKinds {
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp")
    private val FONT_EXT = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIA_EXT = setOf("mp4", "m4v", "webm", "mkv", "mov", "mp3", "m4a", "ogg", "wav", "m3u8", "mpd", "ts", "m4s")

    fun guess(url: String, headers: Map<String, String>?, isMainFrame: Boolean): Int {
        if (isMainFrame) return ResourceType.SUBDOCUMENT
        val ext = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
            .substringAfterLast('.', "").lowercase()
        when {
            ext == "js" || ext == "mjs" -> return ResourceType.SCRIPT
            ext == "css" -> return ResourceType.STYLESHEET
            ext in IMAGE_EXT -> return ResourceType.IMAGE
            ext in FONT_EXT -> return ResourceType.FONT
            ext in MEDIA_EXT -> return ResourceType.MEDIA
            ext == "html" || ext == "htm" -> return ResourceType.SUBDOCUMENT
        }
        val accept = headers?.entries?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
        val requestedWith = headers?.keys?.any { it.equals("X-Requested-With", ignoreCase = true) } == true
        return when {
            accept.startsWith("text/css") -> ResourceType.STYLESHEET
            accept.startsWith("image/") -> ResourceType.IMAGE
            requestedWith -> ResourceType.XHR
            accept.startsWith("text/html") -> ResourceType.SUBDOCUMENT
            else -> ResourceType.OTHER
        }
    }
}
