package ru.spgsroot.peachy.data.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Serves canned pages shaped like the live responses, and records every request. */
private class FakeHttp(private val routes: Map<String, String>) : Http {
    val requests = mutableListOf<String>()

    override fun get(url: String, headers: Map<String, String>): String {
        requests += "GET $url"
        return routes[url] ?: throw HostException("HTTP 404 from $url")
    }

    override fun postJson(url: String, json: String, headers: Map<String, String>): String {
        requests += "POST $url $json"
        return routes["POST $url"] ?: throw HostException("HTTP 404 from $url")
    }
}

class HostImporterTest {
    private fun importer(vararg routes: Pair<String, String>): Pair<HostImporter, FakeHttp> {
        val http = FakeHttp(routes.toMap())
        return HostImporter.withHttp(http) to http
    }

    @Test
    fun linksOfOtherSitesAndUnknownPathsAreNotClaimed() {
        val (importer, _) = importer()
        assertFalse(importer.recognizes("https://example.com/a/abcd1234"))
        assertFalse(importer.recognizes("https://bunkr.cr/"))
        assertFalse(importer.recognizes("https://pixeldrain.com/about"))
        assertFalse(importer.recognizes("not a url"))
        assertTrue(importer.recognizes("https://bunkr.si/a/abcd1234"))
        assertTrue(importer.recognizes("https://cyberdrop.me/f/xyz"))
        assertTrue(importer.recognizes("https://turbo.cr/embed/A294l8fYosRYn"))
        assertTrue(importer.recognizes("https://pixeldrain.com/l/6BU6gfsn"))
        assertTrue(importer.recognizes("https://filester.me/f/1158e261df7b77ce"))
    }

    @Test
    fun bunkrAlbumKeepsOnlyVideosAndResolvesThroughTheSigner() {
        val page = """
            <h1 class="truncate">My &amp; album</h1>
            <script>window.albumFiles = [
              { id: 59373101, name: "a.mp4", original: "Fe\'s clip.mp4", slug: "XEcO", type: "video/mp4", extension: "Video", size: 580206699, timestamp: "17:13:09 06/04/2026" },
              { id: 59373102, name: "b.jpg", original: "photo.jpg", slug: "YYYY", type: "image/jpeg", extension: "Image", size: 87593 },
              { id: 59373103, name: "c.webm", original: "he said \"hi\" {x}.webm", slug: "ZZZZ", type: "video/webm", extension: "Video", size: 10 }
            ];</script>
        """.trimIndent()
        val (importer, http) = importer(
            "https://bunkr.cr/a/abcd1234?advanced=1" to page,
            "POST https://dl.bunkr.cr/api/_001_v2" to
                """{"mediafiles":"https://pc9.scdn.st","original":"Fe's clip.mp4","path":"/storage/media/a.mp4"}""",
            "https://glb-apisign.cdn.cr/sign?path=%2Fstorage%2Fmedia%2Fa.mp4" to """{"ex":1794524075,"token":"d550b90c"}"""
        )

        val entries = importer.expand("https://bunkr.si/a/abcd1234")

        assertEquals(listOf("Fe's clip.mp4", "he said \"hi\" {x}.webm"), entries.map { it.label })
        assertEquals(listOf(580206699L, 10L), entries.map { it.file.size })
        val link = importer.resolve(entries[0])
        assertEquals("https://pc9.scdn.st/storage/media/a.mp4?n=Fe%27s%20clip.mp4&token=d550b90c&ex=1794524075", link.url)
        assertEquals("https://bunkr.cr/", link.headers["Referer"])
        assertTrue(http.requests.any { it == "POST https://dl.bunkr.cr/api/_001_v2 {\"id\":\"59373101\"}" })
    }

    @Test
    fun bunkrSingleFileLinkReadsTheNumericIdFromThePage() {
        val (importer, _) = importer(
            "https://bunkr.cr/f/XEcO7CztrZFTr" to """<a href="https://dl.bunkr.cr/file/59373101">Download</a>"""
        )
        val entry = importer.expand("https://bunkr.cr/f/XEcO7CztrZFTr").single()
        assertEquals("59373101", entry.file.id)
    }

    @Test
    fun cyberdropAlbumListsFilesFromHtml() {
        val page = """
            <h1 id="title" class="title" title="animations &amp; more">animations</h1>
            <div class="image-container"><a id="file" class="image" href="/f/urLPkBXGuNfEg" title="clip one.mp4"></a></div>
            <div class="image-container"><a id="file" href="/f/Second_slug-1" title="pic.gif"></a></div>
        """.trimIndent()
        val (importer, _) = importer(
            "https://cyberdrop.cr/a/HriMgbuf" to page,
            "https://api.cyberdrop.cr/api/file/info/urLPkBXGuNfEg" to """{"name":"clip one.mp4","size":1}""",
            "https://api.cyberdrop.cr/api/file/auth/urLPkBXGuNfEg" to
                """{"url":"https://cdn.cyberdrop.cr/api/file/d/urLPkBXGuNfEg?token=jwt"}"""
        )

        val entries = importer.expand("https://cyberdrop.me/a/HriMgbuf")

        assertEquals(listOf("clip one.mp4"), entries.map { it.label })
        val link = importer.resolve(entries.single())
        assertEquals("https://cdn.cyberdrop.cr/api/file/d/urLPkBXGuNfEg?token=jwt", link.url)
        assertEquals("clip one.mp4", link.name)
    }

    @Test
    fun turboAlbumParsesTheJsLiteralAndResolvesWithSign() {
        val page = """
            <h1 class="text-2xl font-semibold truncate">Test</h1>
            <script>const FILES = [
              { id:"A294l8fYosRYn", name:"wAvr.mp4", size: 1436337 , views: 0 , thumb:"https:\/\/static.scdn.st\/t.jpg", dur: 10 },
              { id:"ofs3eIYQvK_Rb", name:"x-preview.m4v", size: 2222857 , views: 0 },
              { id:"8sCFIDzIe0WC-", name:"cover.jpg", size: 184220 , views: 0 }
            ];</script>
        """.trimIndent()
        val (importer, _) = importer(
            "https://turbo.cr/a/yspN6j69B3z" to page,
            "https://turbo.cr/api/sign?v=A294l8fYosRYn" to
                """{"filename":"A294l8fYosRYn.mp4","original_filename":"wAvr.mp4","success":true,"url":"https://dl100.turbocdn.st/turbo/data/A294l8fYosRYn.mp4?exp=1&token=t&fn=wAvr.mp4"}"""
        )

        val entries = importer.expand("https://turbo.cr/a/yspN6j69B3z")

        assertEquals(listOf("A294l8fYosRYn", "ofs3eIYQvK_Rb"), entries.map { it.file.id })
        val link = importer.resolve(entries[0])
        assertEquals("https://dl100.turbocdn.st/turbo/data/A294l8fYosRYn.mp4?exp=1&token=t&fn=wAvr.mp4", link.url)
        assertEquals("https://turbo.cr/", link.headers["Referer"])
    }

    private val findrAlbum = """
        <div><a class="btn border-primary bg-primary" href="https://turbo.cr/a/yspN6j69B3z">Source<svg xmlns="http://www.w3.org/2000/svg"></svg></a>
        <a href="https://discord.gg/BFF72z3Nds">Discord</a>
        <a href="https://turbo.cr/v/A294l8fYosRYn" aria-hidden="true"></a></div>
    """.trimIndent()

    private val turboAlbum = """
        <h1 class="truncate">Test</h1>
        <script>const FILES = [
          { id:"A294l8fYosRYn", name:"wAvr.mp4", size: 1436337 , views: 0 },
          { id:"8sCFIDzIe0WC-", name:"cover.jpg", size: 184220 , views: 0 }
        ];</script>
    """.trimIndent()

    @Test
    fun findrAlbumLinksAreClaimedButLookalikesAndOtherPagesAreNot() {
        val (importer, _) = importer()
        assertTrue(importer.recognizes("https://findr.to/albums/1881459"))
        assertTrue(importer.recognizes("https://www.findr.to/albums/1881459"))
        assertFalse(importer.recognizes("https://findr.to/"))
        assertFalse(importer.recognizes("https://findr.to/search?tag=video"))
        assertFalse(importer.recognizes("https://findr.to/albums/abc"))
        assertFalse(importer.recognizes("https://findr.to.evil.com/albums/1881459"))
        assertFalse(importer.recognizes("https://notfindr.to/albums/1881459"))
    }

    @Test
    fun findrAlbumImportsTheAlbumItPointsTo() {
        val (importer, http) = importer(
            "https://findr.to/albums/1881459" to findrAlbum,
            "https://turbo.cr/a/yspN6j69B3z" to turboAlbum
        )

        val entries = importer.expand("https://findr.to/albums/1881459")

        assertEquals(listOf("A294l8fYosRYn"), entries.map { it.file.id })
        assertEquals(listOf("GET https://findr.to/albums/1881459", "GET https://turbo.cr/a/yspN6j69B3z"), http.requests)
    }

    @Test
    fun findrAlwaysAsksFindrItselfNotTheHostOfTheLink() {
        val (importer, http) = importer(
            "https://findr.to/albums/7" to findrAlbum,
            "https://turbo.cr/a/yspN6j69B3z" to turboAlbum
        )
        importer.expand("https://www.findr.to/albums/7")
        assertEquals("GET https://findr.to/albums/7", http.requests.first())
    }

    @Test
    fun findrAlbumWithoutASourceOrWithAnUnsupportedOneFailsClearly() {
        val (importer, _) = importer(
            "https://findr.to/albums/1" to "<html>nothing here</html>",
            "https://findr.to/albums/2" to """<a href="https://example.com/a/x">Source<svg></svg></a>""",
            "https://findr.to/albums/3" to """<a href="https://findr.to/albums/3">Source<svg></svg></a>"""
        )
        assertThrows(HostException::class.java) { importer.expand("https://findr.to/albums/1") }
        assertThrows(HostException::class.java) { importer.expand("https://findr.to/albums/2") }
        // A source that points back at findr must not loop.
        assertThrows(HostException::class.java) { importer.expand("https://findr.to/albums/3") }
    }

    @Test
    fun findrSourceLinkEntitiesAreDecoded() {
        assertEquals(
            "https://bunkr.cr/a/abcd1234?x=1&y=2",
            FindrIndex.parseSource("""<a href="https://bunkr.cr/a/abcd1234?x=1&amp;y=2">Source<svg/></a>""")
        )
    }

    @Test
    fun pixeldrainListAndSingleFile() {
        val (importer, _) = importer(
            "https://pixeldrain.com/api/list/6BU6gfsn" to
                """{"success":true,"title":"clips","files":[{"id":"Uw3L8jeh","name":"a.mp4","size":5487206},{"id":"zz","name":"notes.txt","size":3}]}""",
            "https://pixeldrain.com/api/file/Uw3L8jeh/info" to """{"success":true,"id":"Uw3L8jeh","name":"a.mp4","size":5487206}"""
        )

        val album = importer.expand("https://pixeldrain.com/l/6BU6gfsn")
        assertEquals(listOf("a.mp4"), album.map { it.label })
        assertEquals("https://pixeldrain.com/api/file/Uw3L8jeh", importer.resolve(album.single()).url)

        val single = importer.expand("https://pixeldrain.com/u/Uw3L8jeh").single()
        assertEquals("a.mp4", single.label)
    }

    @Test
    fun filesterFollowsPagesUntilTheNextLinkDisappears() {
        fun item(slug: String, name: String) =
            """<div class="file-item" data-type="videos" data-name="$name" data-size="100" data-date="2026-05-30T00:00:00Z" onclick="window.location.href='/d/$slug'"></div>"""
        val (importer, http) = importer(
            "https://filester.me/f/1158e261df7b77ce?page=1" to
                """<h1 class="text-2xl">Folder</h1>${item("AAAAAAA", "one &amp; two.mp4")}${item("BBBBBBB", "pic.jpg")}<a href="?page=2" class="page-link">→</a>""",
            "https://filester.me/f/1158e261df7b77ce?page=2" to item("CCCCCCC", "three.mkv"),
            "POST https://filester.me/v2/api/public/download" to
                """{"expires_in":1800,"file":"06ac.mp4","name":"one & two.mp4","server":"https://fsc1.cdn.cr","success":true,"token":"abc.def"}"""
        )

        val entries = importer.expand("https://filester.me/f/1158e261df7b77ce")

        assertEquals(listOf("one & two.mp4", "three.mkv"), entries.map { it.label })
        assertEquals(2, http.requests.count { it.startsWith("GET") })
        assertEquals("https://fsc1.cdn.cr/v2/06ac.mp4?token=abc.def", importer.resolve(entries[0]).url)
    }

    @Test
    fun albumWithoutVideosFailsWithAReadableMessage() {
        val (importer, _) = importer(
            "https://pixeldrain.com/api/list/imgs" to """{"success":true,"title":"pics","files":[{"id":"a","name":"a.jpg","size":1}]}"""
        )
        val error = assertThrows(HostException::class.java) { importer.expand("https://pixeldrain.com/l/imgs") }
        assertTrue(error.message!!.contains("no videos"))
    }

    @Test
    fun singleLinkToAnImageIsRejectedBeforeAnyDownload() {
        val (importer, _) = importer(
            "https://pixeldrain.com/api/file/img1/info" to """{"success":true,"id":"img1","name":"a.jpg","size":1}"""
        )
        val entry = importer.expand("https://pixeldrain.com/u/img1").single()
        assertThrows(HostException::class.java) { importer.resolve(entry) }
    }

    @Test
    fun unknownLinkIsNotExpanded() {
        val (importer, _) = importer()
        assertThrows(HostException::class.java) { importer.expand("https://example.com/a/1") }
    }

    @Test
    fun doubleEncodedNamesAreRepairedButGenuineAccentsAreNot() {
        assertEquals("Chloé • clip.mp4", "ChloÃ© â\u0080¢ clip.mp4".repairDoubleEncoding())
        assertEquals("café.mp4", "café.mp4".repairDoubleEncoding())
        assertEquals("видео.mp4", "видео.mp4".repairDoubleEncoding())
        assertEquals("plain.mp4", "plain.mp4".repairDoubleEncoding())
    }
}
