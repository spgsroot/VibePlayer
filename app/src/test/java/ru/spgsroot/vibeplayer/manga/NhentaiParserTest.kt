package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class NhentaiParserTest {
    @Test
    fun searchPreservesTagIdsInResponseOrder() {
        val summary = NhentaiParser.search(
            """{"result":[{"id":12,"english_title":"Search title","thumbnail":"thumb.jpg","num_pages":2,"tag_ids":[9,4]}]}"""
        ).single()

        assertEquals(12, summary.id)
        assertEquals("Search title", summary.title)
        assertEquals("thumb.jpg", summary.thumbnailPath)
        assertEquals(2, summary.numPages)
        assertEquals(listOf(9, 4), summary.tagIds)
    }

    @Test
    fun galleryPreservesTagMetadata() {
        val detail = NhentaiParser.gallery(
            """{"id":12,"title":{"pretty":"Pretty title","english":"English title"},"thumbnail":{"path":"thumb.jpg"},"pages":[{"path":"1.jpg"},{"path":"2.png"}],"tags":[{"id":9,"type":"tag","name":"english"}]}"""
        )

        assertEquals(12, detail.id)
        assertEquals("Pretty title", detail.title)
        assertEquals("thumb.jpg", detail.thumbnailPath)
        assertEquals(listOf("1.jpg", "2.png"), detail.pagePaths)
        assertEquals(listOf(GalleryTag(9, "tag", "english")), detail.tags)
    }

    @Test
    fun searchTreatsMissingTagIdsAsEmpty() {
        val summary = NhentaiParser.search("""{"result":[{"id":12}]}""").single()

        assertEquals(emptyList<Int>(), summary.tagIds)
    }

    @Test
    fun galleryTreatsMissingTagsAsEmpty() {
        val detail = NhentaiParser.gallery("""{"id":12}""")

        assertEquals(emptyList<GalleryTag>(), detail.tags)
    }

    @Test
    fun searchTreatsEmptyTagIdsAsEmpty() {
        val summary = NhentaiParser.search("""{"result":[{"id":12,"tag_ids":[]}]}""").single()

        assertEquals(emptyList<Int>(), summary.tagIds)
    }

    @Test
    fun galleryTreatsEmptyTagsAsEmpty() {
        val detail = NhentaiParser.gallery("""{"id":12,"tags":[]}""")

        assertEquals(emptyList<GalleryTag>(), detail.tags)
    }

    @Test
    fun cardFallsBackToJapaneseTitleAndKeepsUnknownFavoritesNull() {
        val cards = NhentaiParser.search(
            """{"result":[{"id":12,"english_title":"","japanese_title":"日本語","num_favorites":0},{"id":13,"english_title":"English","japanese_title":null}]}"""
        )

        assertEquals("日本語", cards[0].title)
        assertEquals(0, cards[0].numFavorites)
        assertEquals("English", cards[1].title)
        assertEquals(null, cards[1].numFavorites)
        assertEquals(emptyList<GalleryTag>(), cards[1].tags)
    }

    @Test
    fun detailKeepsActualMetadataAndConvertsUploadSecondsToMillis() {
        val detail = NhentaiParser.gallery(
            """{"id":12,"title":{"pretty":"","english":"","japanese":"日本語"},"thumbnail":{"path":"thumb.webp"},"upload_date":1700000123,"num_favorites":32,"scanlator":"","tags":[{"id":9,"type":"language","name":"english","count":1234}],"pages":[{"path":"1.webp"}]}"""
        )

        assertEquals("日本語", detail.title)
        assertEquals("日本語", detail.japaneseTitle)
        assertEquals(1700000123000L, detail.uploadedAt)
        assertEquals(32, detail.favoriteCount)
        assertEquals("", detail.scanlator)
        assertEquals(GalleryTag(9, "language", "english", 1234), detail.tags.single())
    }

    @Test
    fun absentDetailMetadataStaysUnknown() {
        val detail = NhentaiParser.gallery("""{"id":12,"title":{"english":"English","japanese":null},"pages":[{"path":"1.jpg"}]}""")

        assertEquals("English", detail.title)
        assertEquals(null, detail.japaneseTitle)
        assertEquals(null, detail.uploadedAt)
        assertEquals(null, detail.favoriteCount)
        assertEquals(null, detail.scanlator)
    }

    @Test
    fun tagPageHasNextOnlyBeforeTheLastServerPage() {
        val body = """{"result":[{"id":9,"type":"tag","name":"school uniform","count":0},{"id":4,"type":"tag","name":"x","count":null}],"num_pages":3}"""

        val first = NhentaiParser.tagsPage(body, page = 1)
        assertEquals(true, first.hasNext)
        assertEquals(0, first.tags[0].count)
        assertEquals(null, first.tags[1].count)
        assertEquals(true, NhentaiParser.tagsPage(body, page = 2).hasNext)
        assertEquals(false, NhentaiParser.tagsPage(body, page = 3).hasNext)
    }

    @Test
    fun tagsBatchAndAutocompleteBodiesAreBareArrays() {
        assertEquals(
            listOf(GalleryTag(9, "tag", "school uniform", 5)),
            NhentaiParser.tags("""[{"id":9,"type":"tag","name":"school uniform","count":5}]""")
        )
    }

    @Test
    fun relatedAcceptsBareAndEnvelopedCards() {
        val card = """{"id":23,"english_title":"Related","thumbnail":"related.webp","tag_ids":[9]}"""

        assertEquals(NhentaiParser.related("[$card]"), NhentaiParser.related("""{"result":[$card]}"""))
        assertEquals(listOf(9), NhentaiParser.related("[$card]").single().tagIds)
    }

    @Test
    fun commentsConvertDatesAndKeepMissingPosterUnknown() {
        val comments = NhentaiParser.comments(
            """{"result":[{"id":3,"poster":{"id":8,"username":"Reader"},"post_date":1700000999,"body":"First <b>line</b>"},{"id":4,"poster":null,"post_date":null,"body":"Text"}],"num_pages":1}"""
        )

        assertEquals(GalleryComment(3, "Reader", "First <b>line</b>", 1700000999000L), comments[0])
        assertEquals(GalleryComment(4, null, "Text", null), comments[1])
    }

    @Test
    fun randomUsesOnlyTheServerId() {
        assertEquals(987654, NhentaiParser.randomGalleryId("""{"id":987654}"""))
    }

    @Test(expected = org.json.JSONException::class)
    fun randomDoesNotInventAnIdWhenMissing() {
        NhentaiParser.randomGalleryId("""{}""")
    }

    @Test(expected = org.json.JSONException::class)
    fun malformedListDoesNotMasqueradeAsAnEmptyPage() {
        NhentaiParser.search("""{"detail":"Unavailable"}""")
    }
}
