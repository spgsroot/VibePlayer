package ru.spgsroot.peachy.manga

fun rememberQuery(current: List<String>, query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return current
    return buildList(10) {
        add(trimmed)
        for (previous in current) {
            if (size == 10) break
            if (previous !in this) add(previous)
        }
    }
}

fun isGalleryId(raw: String): Boolean {
    val trimmed = raw.trim()
    return trimmed.isNotEmpty() && trimmed.all { it in '0'..'9' }
}
