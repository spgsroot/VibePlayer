package ru.spgsroot.peachy.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.spgsroot.peachy.data.db.BookmarkEntity
import ru.spgsroot.peachy.data.db.GalleryMemoryEntity
import ru.spgsroot.peachy.data.db.LibraryGalleryEntity
import ru.spgsroot.peachy.data.db.ReadHistoryEntity
import ru.spgsroot.peachy.data.db.SearchHistoryEntity
import ru.spgsroot.peachy.data.notes.NoteEntity
import ru.spgsroot.peachy.manga.TagCollection
import ru.spgsroot.peachy.manga.TagLibrary
import ru.spgsroot.peachy.manga.TagRef

class VaultMergeTest {
    @Test
    fun theNewerNoteWinsAndEachSideKeepsItsOwn() {
        val current = listOf(NoteEntity("page", "a", "old local", 10), NoteEntity("page", "b", "only local", 5))
        val incoming = listOf(NoteEntity("page", "a", "newer import", 20), NoteEntity("page", "c", "only import", 1))
        val merged = VaultMerge.notes(current, incoming).associate { it.ref to it.text }
        assertEquals(mapOf("a" to "newer import", "b" to "only local", "c" to "only import"), merged)
    }

    @Test
    fun anOlderImportedNoteDoesNotReplaceTheLocalOne() {
        val merged = VaultMerge.notes(listOf(NoteEntity("video", "1", "local", 50)), listOf(NoteEntity("video", "1", "stale", 5)))
        assertEquals("local", merged.single().text)
    }

    @Test
    fun sameRefUnderAnotherKindIsAnotherNote() {
        val merged = VaultMerge.notes(listOf(NoteEntity("page", "1", "p", 1)), listOf(NoteEntity("gallery", "1", "g", 1)))
        assertEquals(2, merged.size)
    }

    @Test
    fun readHistoryOnlyAdvances() {
        val local = listOf(ReadHistoryEntity(1, "t", "x", 5, 10, readAt = 100))
        val incoming = listOf(
            ReadHistoryEntity(1, "t", "x", 2, 10, readAt = 50),
            ReadHistoryEntity(2, "u", "y", 1, 3, readAt = 10)
        )
        assertEquals(listOf(2), VaultMerge.readHistoryToWrite(local, incoming).map { it.galleryId })
        val newer = listOf(ReadHistoryEntity(1, "t", "x", 9, 10, readAt = 200))
        assertEquals(9, VaultMerge.readHistoryToWrite(local, newer).single().page)
    }

    @Test
    fun searchesKeepTheTenNewestDistinct() {
        val current = (1..8).map { SearchHistoryEntity("q$it", it * 10L) }
        val incoming = listOf(SearchHistoryEntity("q1", 1000), SearchHistoryEntity("new", 5), SearchHistoryEntity("new2", 999))
        val merged = VaultMerge.searches(current, incoming)
        assertEquals(10, merged.size)
        assertEquals(listOf("q1", "new2"), merged.take(2).map { it.query })
        assertEquals(merged.map { it.query }.distinct(), merged.map { it.query })
        assertEquals(1000, merged.first().usedAt)
    }

    @Test
    fun tagLibrariesJoinFavoritesAndUniteCollectionsByName() {
        val a = TagRef("tag", "a")
        val b = TagRef("tag", "b")
        val c = TagRef("artist", "c")
        val current = TagLibrary(listOf(a), listOf(TagCollection("Read Later", listOf(a))))
        val incoming = TagLibrary(listOf(a, b), listOf(TagCollection("read later", listOf(b)), TagCollection("Other", listOf(c))))
        val merged = VaultMerge.tagLibrary(current, incoming)
        assertEquals(listOf(a, b), merged.favorites)
        assertEquals(listOf("Read Later", "Other"), merged.collections.map { it.name })
        assertEquals(listOf(a, b), merged.collections.first().tags)
    }

    @Test
    fun videoKeyTellsDifferentVideosApart() {
        assertNotEquals(VaultMerge.videoKey("a", 1, 2), VaultMerge.videoKey("a", 1, 3))
        assertNotEquals(VaultMerge.videoKey("a1", 2, 3), VaultMerge.videoKey("a", 12, 3))
        assertEquals(VaultMerge.videoKey("a", 1, 2), VaultMerge.videoKey("a", 1, 2))
    }

    @Test
    fun theDataDocumentRoundTripsIncludingNulls() {
        val data = VaultData(
            entryCount = 4,
            videos = listOf(VaultVideo(3, "Clip \"q\"\n", 1000, 5, 99, false, "mp4", true)),
            galleries = listOf(LibraryGalleryEntity(7, "G", "t.jpg", "1.jpg\n2.jpg", true, 4)),
            readHistory = listOf(ReadHistoryEntity(7, "G", "t.jpg", 2, 9, 11)),
            searches = listOf(SearchHistoryEntity("мама", 3)),
            blacklist = emptyList(),
            memory = listOf(
                GalleryMemoryEntity(7, "G", "t.jpg", 9, "tags", null, null, 1, false),
                GalleryMemoryEntity(8, "H", "u.jpg", 3, "", 55L, 6, 2, true)
            ),
            notes = listOf(NoteEntity("video", "3", "text\nwith lines", 8)),
            bookmarks = listOf(BookmarkEntity("https://a.b/", "A", 1)),
            browserHistory = emptyList(),
            allowlist = listOf("a.b"),
            tagLibrary = null
        )
        assertEquals(data, VaultData.fromJson(data.toJson()))
    }

    @Test
    fun aDocumentFromANewerFormatOrWithGapsIsRefusedClearly() {
        val newer = org.json.JSONObject(
            VaultData(0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList(), null).toJson()
        ).put("format", 2).toString()
        assertEquals(VaultError.UNSUPPORTED_VERSION, assertThrows(VaultException::class.java) { VaultData.fromJson(newer) }.error)
        assertEquals(VaultError.CORRUPT, assertThrows(VaultException::class.java) { VaultData.fromJson("{\"format\":1}") }.error)
        assertEquals(VaultError.CORRUPT, assertThrows(VaultException::class.java) { VaultData.fromJson("not json") }.error)
    }
}
