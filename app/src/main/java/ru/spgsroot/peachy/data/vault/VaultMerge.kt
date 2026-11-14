package ru.spgsroot.peachy.data.vault

import ru.spgsroot.peachy.data.db.PageTranslationEntity
import ru.spgsroot.peachy.data.db.ReadHistoryEntity
import ru.spgsroot.peachy.data.db.SearchHistoryEntity
import ru.spgsroot.peachy.data.notes.NoteEntity
import ru.spgsroot.peachy.manga.TagCollection
import ru.spgsroot.peachy.manga.TagLibrary
import ru.spgsroot.peachy.translate.PageTranslation

/** How an imported vault is folded into what is already on the device. Nothing local is ever lost to an import. */
object VaultMerge {
    private const val MAX_SEARCHES = 10

    /** Two copies of one video share title, length and size. */
    fun videoKey(title: String, duration: Long, fileSize: Long): String = "$title\u0000$duration\u0000$fileSize"

    /** Photos deduplicate independently of videos, by exact title, encoded dimensions and byte size. */
    fun photoKey(title: String, width: Int, height: Int, fileSize: Long): String =
        "$title\u0000$width\u0000$height\u0000$fileSize"

    /** The newer note wins; a note only on one side is kept. Keyed by kind and ref. */
    fun notes(current: List<NoteEntity>, incoming: List<NoteEntity>): List<NoteEntity> {
        val byKey = current.associateBy { it.kind to it.ref }.toMutableMap()
        for (note in incoming) {
            val key = note.kind to note.ref
            val existing = byKey[key]
            if (existing == null || note.updatedAt > existing.updatedAt) byKey[key] = note
        }
        return byKey.values.toList()
    }

    /** Only the rows that are new or newer than what is stored. */
    fun readHistoryToWrite(current: List<ReadHistoryEntity>, incoming: List<ReadHistoryEntity>): List<ReadHistoryEntity> {
        val have = current.associateBy { it.galleryId }
        return incoming.filter { have[it.galleryId]?.let { old -> it.readAt > old.readAt } ?: true }
    }

    /** The newest distinct queries, as many as the search box keeps. */
    fun searches(current: List<SearchHistoryEntity>, incoming: List<SearchHistoryEntity>): List<SearchHistoryEntity> =
        (current + incoming)
            .sortedByDescending { it.usedAt }
            .distinctBy { it.query }
            .take(MAX_SEARCHES)

    /** Favorites are joined; a collection of the same name (ignoring case) takes the union of its tags. */
    fun tagLibrary(current: TagLibrary, incoming: TagLibrary): TagLibrary {
        val favorites = (current.favorites + incoming.favorites).distinct()
        val collections = current.collections.toMutableList()
        for (collection in incoming.collections) {
            val at = collections.indexOfFirst { it.name.equals(collection.name, ignoreCase = true) }
            if (at < 0) {
                collections += collection
            } else {
                val have = collections[at]
                collections[at] = TagCollection(have.name, (have.tags + collection.tags).distinct())
            }
        }
        return TagLibrary(favorites, collections)
    }

    /**
     * The rows an import has to write: pages the device has no translation for, and pages whose imported
     * copy is strictly newer. A tie keeps the local row, and an imported row that no longer parses is
     * skipped so it cannot replace a good one. Nothing is deleted.
     */
    fun translations(current: List<PageTranslationEntity>, incoming: List<PageTranslationEntity>): List<PageTranslationEntity> {
        val local = current.associateBy { Triple(it.galleryId, it.page, it.lang) }
        val newest = LinkedHashMap<Triple<Int, Int, String>, PageTranslationEntity>()
        for (row in incoming) {
            if (PageTranslation.parse(row.json) == null) continue
            val key = Triple(row.galleryId, row.page, row.lang)
            val have = local[key]
            if (have != null && row.createdAt <= have.createdAt) continue
            val seen = newest[key]
            if (seen == null || row.createdAt > seen.createdAt) newest[key] = row
        }
        return newest.values.toList()
    }
}
