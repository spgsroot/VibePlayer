package ru.spgsroot.peachy.data.vault

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.spgsroot.peachy.data.db.BookmarkEntity
import ru.spgsroot.peachy.data.db.BrowserHistoryEntity
import ru.spgsroot.peachy.data.db.GalleryMemoryEntity
import ru.spgsroot.peachy.data.db.LibraryGalleryEntity
import ru.spgsroot.peachy.data.db.ReadHistoryEntity
import ru.spgsroot.peachy.data.db.SearchHistoryEntity
import ru.spgsroot.peachy.data.db.TagBlacklistEntity
import ru.spgsroot.peachy.data.notes.NoteEntity

/** A video as the vault describes it; the file itself is a separate entry named by [id] and [extension]. */
data class VaultVideo(
    val id: Long,
    val title: String,
    val duration: Long,
    val addedAt: Long,
    val fileSize: Long,
    val isCorrupted: Boolean,
    val extension: String,
    val hasThumbnail: Boolean
) {
    val entryName: String get() = "video/$id.$extension"
    val thumbnailEntryName: String get() = "thumb/$id"
}

/**
 * Everything the vault carries besides files, as one JSON document. [entryCount] is the number of
 * file entries after it, so an import can show progress.
 */
data class VaultData(
    val entryCount: Int,
    val videos: List<VaultVideo>,
    val galleries: List<LibraryGalleryEntity>,
    val readHistory: List<ReadHistoryEntity>,
    val searches: List<SearchHistoryEntity>,
    val blacklist: List<TagBlacklistEntity>,
    val memory: List<GalleryMemoryEntity>,
    val notes: List<NoteEntity>,
    val bookmarks: List<BookmarkEntity>,
    val browserHistory: List<BrowserHistoryEntity>,
    val allowlist: List<String>,
    val tagLibrary: String?
) {
    fun toJson(): String = JSONObject()
        .put("format", FORMAT)
        .put("entryCount", entryCount)
        .put("videos", array(videos) {
            JSONObject().put("id", it.id).put("title", it.title).put("duration", it.duration)
                .put("addedAt", it.addedAt).put("fileSize", it.fileSize).put("isCorrupted", it.isCorrupted)
                .put("extension", it.extension).put("hasThumbnail", it.hasThumbnail)
        })
        .put("galleries", array(galleries) {
            JSONObject().put("id", it.id).put("title", it.title).put("thumbnailPath", it.thumbnailPath)
                .put("pagePaths", it.pagePaths).put("favorite", it.favorite).put("addedAt", it.addedAt)
        })
        .put("readHistory", array(readHistory) {
            JSONObject().put("galleryId", it.galleryId).put("title", it.title).put("thumbnailPath", it.thumbnailPath)
                .put("page", it.page).put("pageCount", it.pageCount).put("readAt", it.readAt)
        })
        .put("searches", array(searches) { JSONObject().put("query", it.query).put("usedAt", it.usedAt) })
        .put("blacklist", array(blacklist) { JSONObject().put("kind", it.kind).put("value", it.value) })
        .put("memory", array(memory) {
            JSONObject().put("galleryId", it.galleryId).put("title", it.title).put("thumbnailPath", it.thumbnailPath)
                .put("numPages", it.numPages).put("tags", it.tags).put("uploadedAt", it.uploadedAt ?: JSONObject.NULL)
                .put("numFavorites", it.numFavorites ?: JSONObject.NULL).put("seenAt", it.seenAt)
                .put("dismissed", it.dismissed)
        })
        .put("notes", array(notes) {
            JSONObject().put("kind", it.kind).put("ref", it.ref).put("text", it.text).put("updatedAt", it.updatedAt)
        })
        .put("bookmarks", array(bookmarks) {
            JSONObject().put("url", it.url).put("title", it.title).put("addedAt", it.addedAt)
        })
        .put("browserHistory", array(browserHistory) {
            JSONObject().put("url", it.url).put("title", it.title).put("visitedAt", it.visitedAt)
        })
        .put("allowlist", JSONArray(allowlist))
        .put("tagLibrary", tagLibrary ?: JSONObject.NULL)
        .toString()

    companion object {
        const val FORMAT = 1

        /** A document that cannot be understood is a damaged vault; a newer format is reported as such. */
        fun fromJson(raw: String): VaultData {
            try {
                val root = JSONObject(raw)
                if (root.getInt("format") != FORMAT) {
                    throw VaultException(VaultError.UNSUPPORTED_VERSION, "Vault data format ${root.getInt("format")}")
                }
                return VaultData(
                    entryCount = root.getInt("entryCount"),
                    videos = list(root, "videos") {
                        VaultVideo(
                            it.getLong("id"), it.getString("title"), it.getLong("duration"), it.getLong("addedAt"),
                            it.getLong("fileSize"), it.getBoolean("isCorrupted"), it.getString("extension"),
                            it.getBoolean("hasThumbnail")
                        )
                    },
                    galleries = list(root, "galleries") {
                        LibraryGalleryEntity(
                            it.getInt("id"), it.getString("title"), it.getString("thumbnailPath"),
                            it.getString("pagePaths"), it.getBoolean("favorite"), it.getLong("addedAt")
                        )
                    },
                    readHistory = list(root, "readHistory") {
                        ReadHistoryEntity(
                            it.getInt("galleryId"), it.getString("title"), it.getString("thumbnailPath"),
                            it.getInt("page"), it.getInt("pageCount"), it.getLong("readAt")
                        )
                    },
                    searches = list(root, "searches") { SearchHistoryEntity(it.getString("query"), it.getLong("usedAt")) },
                    blacklist = list(root, "blacklist") { TagBlacklistEntity(it.getString("kind"), it.getString("value")) },
                    memory = list(root, "memory") {
                        GalleryMemoryEntity(
                            it.getInt("galleryId"), it.getString("title"), it.getString("thumbnailPath"),
                            it.getInt("numPages"), it.getString("tags"),
                            if (it.isNull("uploadedAt")) null else it.getLong("uploadedAt"),
                            if (it.isNull("numFavorites")) null else it.getInt("numFavorites"),
                            it.getLong("seenAt"), it.getBoolean("dismissed")
                        )
                    },
                    notes = list(root, "notes") {
                        NoteEntity(it.getString("kind"), it.getString("ref"), it.getString("text"), it.getLong("updatedAt"))
                    },
                    bookmarks = list(root, "bookmarks") {
                        BookmarkEntity(it.getString("url"), it.getString("title"), it.getLong("addedAt"))
                    },
                    browserHistory = list(root, "browserHistory") {
                        BrowserHistoryEntity(it.getString("url"), it.getString("title"), it.getLong("visitedAt"))
                    },
                    allowlist = buildList {
                        val items = root.getJSONArray("allowlist")
                        for (i in 0 until items.length()) add(items.getString(i))
                    },
                    tagLibrary = if (root.isNull("tagLibrary")) null else root.getString("tagLibrary")
                )
            } catch (e: JSONException) {
                throw VaultException(VaultError.CORRUPT, "Vault data is unreadable: ${e.message}")
            }
        }

        private fun <T> array(items: List<T>, encode: (T) -> JSONObject) = JSONArray(items.map(encode))

        private fun <T> list(root: JSONObject, key: String, decode: (JSONObject) -> T): List<T> {
            val items = root.getJSONArray(key)
            return List(items.length()) { decode(items.getJSONObject(it)) }
        }
    }
}
