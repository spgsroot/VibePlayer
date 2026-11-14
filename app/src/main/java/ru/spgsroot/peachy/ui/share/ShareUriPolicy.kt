package ru.spgsroot.peachy.ui.share

enum class SharedMediaKind { PHOTO, VIDEO }

object ShareUriPolicy {
    fun isImportable(scheme: String?): Boolean = scheme == "content"

    /** A provider's concrete MIME type wins over the batch type, including mixed image/video shares. */
    fun mediaKind(providerType: String?, intentType: String?): SharedMediaKind? {
        val concrete = providerType?.substringBefore(';')?.trim()?.lowercase()
        val type = if (concrete.isNullOrBlank() || concrete == "*/*" || concrete == "application/octet-stream") {
            intentType?.substringBefore(';')?.trim()?.lowercase()
        } else concrete
        return when {
            type?.startsWith("image/") == true -> SharedMediaKind.PHOTO
            type?.startsWith("video/") == true -> SharedMediaKind.VIDEO
            else -> null
        }
    }
}
