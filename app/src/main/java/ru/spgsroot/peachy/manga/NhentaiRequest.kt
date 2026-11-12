package ru.spgsroot.peachy.manga

data class NhentaiRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null
) {
    fun header(name: String): String? = headers[name]

    companion object {
        const val USER_AGENT = "Peachy/2.4.0 (https://github.com/spgsroot/VibePlayer)"

        fun favorite(galleryId: Int, key: String, add: Boolean): NhentaiRequest {
            return NhentaiRequest(
                method = if (add) "POST" else "DELETE",
                url = "https://nhentai.net/api/v2/galleries/$galleryId/favorite",
                headers = authHeaders(key)
            )
        }

        fun search(query: String, sort: String, page: Int, key: String): NhentaiRequest {
            // The API rejects an empty query (400); `*` is the browse-everything query.
            val encodedQuery = java.net.URLEncoder.encode(query.ifBlank { "*" }, Charsets.UTF_8.name())
            val encodedSort = java.net.URLEncoder.encode(sort, Charsets.UTF_8.name())
            return NhentaiRequest(
                method = "GET",
                url = "https://nhentai.net/api/v2/search?query=$encodedQuery&sort=$encodedSort&page=$page",
                headers = authHeaders(key)
            )
        }

        fun gallery(galleryId: Int, key: String): NhentaiRequest {
            return NhentaiRequest(
                method = "GET",
                url = "https://nhentai.net/api/v2/galleries/$galleryId",
                headers = authHeaders(key)
            )
        }

        fun tagsByIds(ids: Collection<Int>, key: String): NhentaiRequest {
            return get("https://nhentai.net/api/v2/tags/ids?ids=${ids.joinToString(",")}", key)
        }

        fun tagList(type: String, page: Int, perPage: Int, key: String): NhentaiRequest {
            val encodedType = java.net.URLEncoder.encode(type, Charsets.UTF_8.name())
            return get(
                "https://nhentai.net/api/v2/tags/$encodedType?sort=popular&page=$page&per_page=$perPage",
                key
            )
        }

        fun tagSearch(type: String, query: String, limit: Int, key: String): NhentaiRequest {
            val body = org.json.JSONObject().put("query", query).put("type", type).put("limit", limit).toString()
            return NhentaiRequest(
                method = "POST",
                url = "https://nhentai.net/api/v2/tags/search",
                headers = authHeaders(key) + ("Content-Type" to "application/json"),
                body = body
            )
        }

        fun random(key: String): NhentaiRequest = get("https://nhentai.net/api/v2/galleries/random", key)

        fun related(galleryId: Int, key: String): NhentaiRequest =
            get("https://nhentai.net/api/v2/galleries/$galleryId/related", key)

        fun comments(galleryId: Int, key: String): NhentaiRequest =
            get("https://nhentai.net/api/v2/galleries/$galleryId/comments?page=1&per_page=50", key)

        private fun get(url: String, key: String): NhentaiRequest {
            return NhentaiRequest(method = "GET", url = url, headers = authHeaders(key))
        }

        private fun authHeaders(key: String): Map<String, String> {
            return mapOf(
                "Authorization" to "Key $key",
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json"
            )
        }
    }
}
