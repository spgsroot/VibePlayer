package ru.spgsroot.peachy.manga

fun blocks(
    tagIds: Collection<Int>,
    tagNames: Collection<String>,
    blockedIds: Set<Int>,
    blockedNames: Set<String>
): Boolean {
    return tagIds.any { it in blockedIds } || tagNames.any { name ->
        blockedNames.any { name.equals(it, ignoreCase = true) }
    }
}

internal suspend fun filterSearchResults(
    results: List<GallerySummary>,
    knownTagNames: Map<Int, String>,
    isBlocked: suspend (Collection<Int>, Collection<String>) -> Boolean
): List<GallerySummary> = results.filterNot {
    isBlocked(it.tagIds, it.tagIds.mapNotNull(knownTagNames::get))
}
