package ru.spgsroot.peachy.manga

sealed class SearchDestination {
    data class OpenGallery(val id: Int) : SearchDestination()
    data object RunSearch : SearchDestination()
}

fun searchDestination(raw: String): SearchDestination {
    val id = raw.trim().takeIf(::isGalleryId)?.toIntOrNull()
    return if (id != null) SearchDestination.OpenGallery(id) else SearchDestination.RunSearch
}

fun customUploadedRange(start: String, end: String): String {
    if (start.isNotBlank() && end.isNotBlank() && end < start) return ""
    return buildString {
        if (start.isNotBlank()) append("uploaded:>").append(start)
        if (end.isNotBlank()) {
            separatePart()
            append("uploaded:<").append(end)
        }
    }
}

enum class TagMode { Include, Exclude }

data class TagFilter(
    val type: String,
    val name: String,
    val mode: TagMode
)

enum class SortKey { Date, Popular, PopularToday, PopularWeek, PopularMonth }

fun SortKey.toApi(): String = when (this) {
    SortKey.Date -> "date"
    SortKey.Popular -> "popular"
    SortKey.PopularToday -> "popular-today"
    SortKey.PopularWeek -> "popular-week"
    SortKey.PopularMonth -> "popular-month"
}

data class PagesFilter(
    val min: Int? = null,
    val max: Int? = null,
    val exact: Int? = null
)

data class CatalogQuery(
    val text: String = "",
    val tags: List<TagFilter> = emptyList(),
    val sort: SortKey = SortKey.Date,
    val uploaded: String = "",
    val pages: PagesFilter = PagesFilter()
) {
    fun toSearchQuery(): String = buildString {
        append(text.trim())
        for (tag in tags) {
            separatePart()
            if (tag.mode == TagMode.Exclude) append('-')
            append(tag.type).append(':')
            val quoted = ' ' in tag.name
            if (quoted) append('"')
            append(tag.name)
            if (quoted) append('"')
        }
        if (uploaded.isNotBlank()) {
            separatePart()
            append(uploaded)
        }
        val exact = pages.exact
        if (exact != null) {
            if (exact > 0) {
                separatePart()
                append("pages:").append(exact)
            }
        } else {
            val min = pages.min
            if (min != null && min > 0) {
                separatePart()
                append("pages:>").append(min)
            }
            val max = pages.max
            if (max != null && max > 0) {
                separatePart()
                append("pages:<").append(max)
            }
        }
    }
}

fun nextTagMode(mode: TagMode?): TagMode? = when (mode) {
    null -> TagMode.Include
    TagMode.Include -> TagMode.Exclude
    TagMode.Exclude -> null
}

private fun StringBuilder.separatePart() {
    if (isNotEmpty()) append(' ')
}
