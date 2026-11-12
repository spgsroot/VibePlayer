package ru.spgsroot.vibeplayer.data.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.spgsroot.vibeplayer.data.db.ReadHistoryDao
import ru.spgsroot.vibeplayer.data.db.ReadHistoryEntity
import ru.spgsroot.vibeplayer.data.db.SearchHistoryDao
import ru.spgsroot.vibeplayer.data.db.TagBlacklistDao
import ru.spgsroot.vibeplayer.data.db.TagBlacklistEntity
import ru.spgsroot.vibeplayer.manga.GalleryTag
import ru.spgsroot.vibeplayer.manga.blocks
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaCatalogStore @Inject constructor(
    private val readHistoryDao: ReadHistoryDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val tagBlacklistDao: TagBlacklistDao
) {
    val history: Flow<List<ReadHistoryEntity>> = readHistoryDao.observeAll()
    val queries: Flow<List<String>> = searchHistoryDao.observeAll().map { rows -> rows.map { it.query } }

    suspend fun getHistory(galleryId: Int): ReadHistoryEntity? = readHistoryDao.getById(galleryId)

    suspend fun upsertHistory(entity: ReadHistoryEntity) = readHistoryDao.upsert(entity)

    suspend fun rememberQuery(query: String) = searchHistoryDao.remember(query, System.currentTimeMillis())

    suspend fun blockedIds(): Set<Int> = tagBlacklistDao.getAll().ids()

    suspend fun blockedNames(): Set<String> = tagBlacklistDao.getAll().names()

    suspend fun blockId(id: Int) = tagBlacklistDao.upsert(TagBlacklistEntity("id", id.toString()))

    suspend fun unblockId(id: Int) = tagBlacklistDao.delete("id", id.toString())

    suspend fun blockName(name: String) = tagBlacklistDao.upsert(TagBlacklistEntity("name", name.lowercase()))

    suspend fun unblockName(name: String) = tagBlacklistDao.delete("name", name.lowercase())

    private val knownNames = ConcurrentHashMap<Int, String>()
    private val _knownGeneration = MutableStateFlow(0)
    val knownGeneration: Flow<Int> = _knownGeneration

    fun rememberTags(tags: List<GalleryTag>) {
        var changed = false
        for (tag in tags) {
            val name = tag.name.lowercase()
            if (knownNames.put(tag.id, name) != name) changed = true
        }
        if (changed) _knownGeneration.value = _knownGeneration.value + 1
    }

    suspend fun blocks(tagIds: Collection<Int>, tagNames: Collection<String>): Boolean {
        val rows = tagBlacklistDao.getAll()
        val names = buildList {
            addAll(tagNames)
            for (id in tagIds) knownNames[id]?.let(::add)
        }
        return blocks(tagIds, names, rows.ids(), rows.names())
    }
}

private fun List<TagBlacklistEntity>.ids(): Set<Int> = buildSet {
    for (row in this@ids) {
        if (row.kind == "id") add(row.value.toInt())
    }
}

private fun List<TagBlacklistEntity>.names(): Set<String> = buildSet {
    for (row in this@names) {
        if (row.kind == "name") add(row.value.lowercase())
    }
}
