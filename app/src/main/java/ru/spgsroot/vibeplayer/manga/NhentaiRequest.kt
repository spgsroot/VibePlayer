package ru.spgsroot.vibeplayer.manga

data class NhentaiRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>
) {
    fun header(name: String): String? = headers[name]

    companion object {
        const val USER_AGENT = "VibePlayer/2.3.0 (https://github.com/spgsroot/VibePlayer)"

        fun favorite(galleryId: Int, key: String, add: Boolean): NhentaiRequest {
            return NhentaiRequest(
                method = if (add) "POST" else "DELETE",
                url = "https://nhentai.net/api/v2/galleries/$galleryId/favorite",
                headers = authHeaders(key)
            )
        }

        fun search(query: String, sort: String, page: Int, key: String): NhentaiRequest {
            val encodedQuery = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
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

        private fun authHeaders(key: String): Map<String, String> {
            return mapOf(
                "Authorization" to "Key $key",
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json"
            )
        }
    }
}
