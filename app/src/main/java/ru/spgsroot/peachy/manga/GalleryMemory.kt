package ru.spgsroot.peachy.manga

/** What the app remembers about a gallery it has shown: enough for a card and for learning tastes. */
data class RememberedGallery(
    val id: Int,
    val title: String,
    val thumbnailPath: String,
    val numPages: Int,
    val tags: List<GalleryTag>,
    val uploadedAt: Long?,
    val numFavorites: Int?,
    val seenAt: Long,
    val dismissed: Boolean
)

fun GalleryDetail.toRemembered(seenAt: Long, dismissed: Boolean = false) = RememberedGallery(
    id = id,
    title = title,
    thumbnailPath = thumbnailPath,
    numPages = pagePaths.size,
    tags = tags,
    uploadedAt = uploadedAt,
    numFavorites = favoriteCount,
    seenAt = seenAt,
    dismissed = dismissed
)

fun GallerySummary.toRemembered(seenAt: Long, dismissed: Boolean = false) = RememberedGallery(
    id = id,
    title = title,
    thumbnailPath = thumbnailPath,
    numPages = numPages,
    tags = tags,
    uploadedAt = null,
    numFavorites = numFavorites,
    seenAt = seenAt,
    dismissed = dismissed
)

/** The language a gallery is written in; `translated` only says it is a translation, not which language. */
fun List<GalleryTag>.language(): String? =
    firstOrNull { it.type == "language" && it.name != "translated" }?.name

/** Tags are stored one per line as `id TAB type TAB name TAB count`; the count is empty when unknown. */
object TagCodec {
    fun encode(tags: List<GalleryTag>): String = tags.joinToString("\n") { tag ->
        listOf(tag.id.toString(), tag.type.clean(), tag.name.clean(), tag.count?.toString().orEmpty())
            .joinToString("\t")
    }

    fun decode(encoded: String): List<GalleryTag> = encoded.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4) return@mapNotNull null
        val id = parts[0].toIntOrNull() ?: return@mapNotNull null
        if (parts[1].isEmpty() || parts[2].isEmpty()) return@mapNotNull null
        GalleryTag(id, parts[1], parts[2], parts[3].toIntOrNull())
    }.toList()

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')
}
