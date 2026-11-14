package ru.spgsroot.peachy.data.storage

internal object PhotoImportPolicy {
    const val THUMBNAIL_SIZE = 512

    fun requireImageBounds(width: Int, height: Int) {
        require(width > 0 && height > 0) { "The saved file is not a readable image" }
    }

    fun sampleSize(width: Int, height: Int): Int {
        requireImageBounds(width, height)
        var sample = 1
        while (maxOf(width, height) / sample > THUMBNAIL_SIZE) sample *= 2
        return sample
    }

    fun title(displayName: String?): String {
        val name = displayName.orEmpty().replace('\\', '/').substringAfterLast('/')
            .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
        val title = name.substringBeforeLast('.', name).trim().take(200)
        return title.takeUnless { it.isBlank() || it.all { char -> char == '.' } } ?: "Photo"
    }
}
