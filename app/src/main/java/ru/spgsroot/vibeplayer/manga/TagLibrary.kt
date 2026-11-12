package ru.spgsroot.vibeplayer.manga

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class TagRef(val type: String, val name: String)

data class TagCollection(val name: String, val tags: List<TagRef> = emptyList())

/** Locally saved tag favorites and named tag collections. Immutable; every edit returns a new value. */
data class TagLibrary(
    val favorites: List<TagRef> = emptyList(),
    val collections: List<TagCollection> = emptyList()
) {
    fun isFavorite(tag: TagRef): Boolean = tag in favorites

    fun toggleFavorite(tag: TagRef): TagLibrary =
        copy(favorites = if (tag in favorites) favorites - tag else favorites + tag)

    /** Names are unique ignoring case; a blank or duplicate name changes nothing. */
    fun createCollection(name: String): TagLibrary {
        val clean = name.trim()
        if (clean.isEmpty() || collections.any { it.name.equals(clean, ignoreCase = true) }) return this
        return copy(collections = collections + TagCollection(clean))
    }

    fun deleteCollection(name: String): TagLibrary =
        copy(collections = collections.filterNot { it.name == name })

    fun renameCollection(name: String, newName: String): TagLibrary {
        val clean = newName.trim()
        val clashes = collections.any { it.name != name && it.name.equals(clean, ignoreCase = true) }
        if (clean.isEmpty() || clashes) return this
        return copy(collections = collections.map { if (it.name == name) it.copy(name = clean) else it })
    }

    fun toggleInCollection(name: String, tag: TagRef): TagLibrary = copy(
        collections = collections.map { collection ->
            if (collection.name != name) collection
            else collection.copy(tags = if (tag in collection.tags) collection.tags - tag else collection.tags + tag)
        }
    )

    fun toJson(): String = JSONObject()
        .put("favorites", JSONArray(favorites.map(::tagJson)))
        .put(
            "collections",
            JSONArray(collections.map { JSONObject().put("name", it.name).put("tags", JSONArray(it.tags.map(::tagJson))) })
        )
        .toString()

    companion object {
        /** Unreadable stored data is treated as an empty library rather than crashing the screen. */
        fun fromJson(raw: String?): TagLibrary {
            if (raw.isNullOrBlank()) return TagLibrary()
            return try {
                val root = JSONObject(raw)
                TagLibrary(
                    favorites = tagRefs(root.optJSONArray("favorites")),
                    collections = buildList {
                        val items = root.optJSONArray("collections") ?: return@buildList
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            val name = item.optString("name").trim()
                            if (name.isEmpty() || any { it.name.equals(name, ignoreCase = true) }) continue
                            add(TagCollection(name, tagRefs(item.optJSONArray("tags"))))
                        }
                    }
                )
            } catch (_: JSONException) {
                TagLibrary()
            }
        }

        private fun tagJson(tag: TagRef) = JSONObject().put("type", tag.type).put("name", tag.name)

        private fun tagRefs(items: JSONArray?): List<TagRef> = buildList {
            if (items == null) return@buildList
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val type = item.optString("type")
                val name = item.optString("name")
                if (type.isEmpty() || name.isEmpty()) continue
                val tag = TagRef(type, name)
                if (tag !in this) add(tag)
            }
        }
    }
}

/** Cycles one tag through include, exclude, removed; the same order as the filter sheet. */
fun List<TagFilter>.cycled(type: String, name: String): List<TagFilter> {
    val mode = nextTagMode(find { it.type == type && it.name == name }?.mode)
    val rest = filterNot { it.type == type && it.name == name }
    return if (mode == null) rest else rest + TagFilter(type, name, mode)
}
