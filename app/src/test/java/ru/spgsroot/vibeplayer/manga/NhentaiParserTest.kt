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
}
