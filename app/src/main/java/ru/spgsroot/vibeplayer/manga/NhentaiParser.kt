package ru.spgsroot.vibeplayer.manga

import org.json.JSONArray
import org.json.JSONObject

data class GallerySummary(
    val id: Int,
    val title: String,
    val thumbnailPath: String,
    val numPages: Int,
    val tagIds: List<Int> = emptyList(),
    val tags: List<GalleryTag> = emptyList(),
    val numFavorites: Int? = null
)

data class GalleryDetail(
    val id: Int,
    val title: String,
    val thumbnailPath: String,
    val pagePaths: List<String>,
    val tags: List<GalleryTag> = emptyList(),
    val japaneseTitle: String? = null,
    val uploadedAt: Long? = null,
    val favoriteCount: Int? = null,
    val scanlator: String? = null,
    /** Thumbnail path of each page, aligned with [pagePaths]; blank when the API sent none. */
    val pageThumbs: List<String> = emptyList()
)

data class GalleryTag(
    val id: Int,
    val type: String,
    val name: String,
    val count: Int? = null
)

data class TagPage(
    val tags: List<GalleryTag>,
    val hasNext: Boolean
)

data class GalleryComment(
    val id: Int,
    val username: String?,
    val body: String,
    val postedAt: Long?
)

object NhentaiParser {
    fun search(body: String): List<GallerySummary> = cards(JSONObject(body).getJSONArray("result"))

    fun related(body: String): List<GallerySummary> = cards(resultArray(body))

    fun gallery(body: String): GalleryDetail {
        val json = JSONObject(body)
        val title = json.optJSONObject("title")
        val pretty = title?.optString("pretty").orEmpty()
        val english = title?.optString("english").orEmpty()
        val japanese = title?.nullableString("japanese")
        val pages = json.optJSONArray("pages")
        val pagePaths = mutableListOf<String>()
        val pageThumbs = mutableListOf<String>()
        if (pages != null) {
            for (index in 0 until pages.length()) {
                val page = pages.optJSONObject(index) ?: continue
                val path = page.optString("path").orEmpty()
                if (path.isBlank()) continue
                pagePaths += path
                pageThumbs += page.optString("thumbnail").orEmpty()
            }
        }
        return GalleryDetail(
            id = json.optInt("id"),
            title = pretty.ifBlank { english }.ifBlank { japanese.orEmpty() },
            thumbnailPath = json.optJSONObject("thumbnail")?.optString("path").orEmpty(),
            pagePaths = pagePaths,
            tags = json.optJSONArray("tags")?.let(::tagList) ?: emptyList(),
            japaneseTitle = japanese,
            uploadedAt = json.nullableLong("upload_date")?.let { it * MILLIS_PER_SECOND },
            favoriteCount = json.nullableInt("num_favorites"),
            scanlator = json.nullableString("scanlator"),
            pageThumbs = pageThumbs
        )
    }

    /** `/tags/ids` and `/tags/search` answer with a bare array of tag objects. */
    fun tags(body: String): List<GalleryTag> = tagList(JSONArray(body))

    /** `/tags/{type}` answers with `{result, num_pages}`; [page] is the one that was requested. */
    fun tagsPage(body: String, page: Int): TagPage {
        val json = JSONObject(body)
        return TagPage(
            tags = tagList(json.getJSONArray("result")),
            hasNext = page < json.optInt("num_pages")
        )
    }

    fun comments(body: String): List<GalleryComment> {
        val items = resultArray(body)
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    GalleryComment(
                        id = item.optInt("id"),
                        username = item.optJSONObject("poster")?.nullableString("username"),
                        body = item.optString("body"),
                        postedAt = item.nullableLong("post_date")?.let { it * MILLIS_PER_SECOND }
                    )
                )
            }
        }
    }

    fun randomGalleryId(body: String): Int = JSONObject(body).getInt("id")

    private const val MILLIS_PER_SECOND = 1000L

    /** Lists arrive either bare or wrapped in `{result: [...]}`. */
    private fun resultArray(body: String): JSONArray {
        val trimmed = body.trimStart()
        return if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("result")
    }

    private fun cards(result: JSONArray): List<GallerySummary> = buildList {
        for (index in 0 until result.length()) {
            val item = result.optJSONObject(index) ?: continue
            add(
                GallerySummary(
                    id = item.optInt("id"),
                    title = item.optString("english_title").ifBlank { item.nullableString("japanese_title").orEmpty() },
                    thumbnailPath = item.optString("thumbnail"),
                    numPages = item.optInt("num_pages"),
                    tagIds = item.optJSONArray("tag_ids")?.let { ids ->
                        List(ids.length()) { ids.optInt(it) }
                    } ?: emptyList(),
                    numFavorites = item.nullableInt("num_favorites")
                )
            )
        }
    }

    private fun tagList(items: JSONArray): List<GalleryTag> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            add(
                GalleryTag(
                    id = item.optInt("id"),
                    type = item.optString("type"),
                    name = item.optString("name"),
                    count = item.nullableInt("count")
                )
            )
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key)

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)
}
