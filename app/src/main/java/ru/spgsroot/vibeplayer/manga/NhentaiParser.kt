package ru.spgsroot.vibeplayer.manga

import org.json.JSONObject

data class GallerySummary(
    val id: Int,
    val title: String,
    val thumbnailPath: String,
    val numPages: Int
)

data class GalleryDetail(
    val id: Int,
    val title: String,
    val thumbnailPath: String,
    val pagePaths: List<String>
)

object NhentaiParser {
    fun search(body: String): List<GallerySummary> {
        val result = JSONObject(body).optJSONArray("result") ?: return emptyList()
        return buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                add(
                    GallerySummary(
                        id = item.optInt("id"),
                        title = item.optString("english_title"),
                        thumbnailPath = item.optString("thumbnail"),
                        numPages = item.optInt("num_pages")
                    )
                )
            }
        }
    }

    fun gallery(body: String): GalleryDetail {
        val json = JSONObject(body)
        val title = json.optJSONObject("title")
        val pretty = title?.optString("pretty").orEmpty()
        val english = title?.optString("english").orEmpty()
        val pages = json.optJSONArray("pages")
        val pagePaths = buildList {
            if (pages != null) {
                for (index in 0 until pages.length()) {
                    val path = pages.optJSONObject(index)?.optString("path").orEmpty()
                    if (path.isNotBlank()) add(path)
                }
            }
        }
        return GalleryDetail(
            id = json.optInt("id"),
            title = pretty.ifBlank { english },
            thumbnailPath = json.optJSONObject("thumbnail")?.optString("path").orEmpty(),
            pagePaths = pagePaths
        )
    }
}
