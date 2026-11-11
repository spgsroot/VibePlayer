package ru.spgsroot.vibeplayer.manga

object NhentaiCdn {
    const val IMAGE_HOST = "https://i1.nhentai.net"
    const val THUMB_HOST = "https://t1.nhentai.net"

    fun image(path: String): String? = join(IMAGE_HOST, path)

    fun thumb(path: String): String? = join(THUMB_HOST, path)

    fun join(host: String, path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed.contains("..") || trimmed.contains("://") || trimmed.startsWith("//")) {
            return null
        }
        val relative = trimmed.trimStart('/')
        if (relative.isEmpty()) return null
        return host.trimEnd('/') + "/" + relative
    }
}
