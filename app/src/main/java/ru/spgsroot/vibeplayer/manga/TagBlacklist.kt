package ru.spgsroot.vibeplayer.manga

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
