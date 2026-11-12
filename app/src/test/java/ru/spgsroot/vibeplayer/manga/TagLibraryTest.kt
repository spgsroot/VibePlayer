package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagLibraryTest {
    private val uniform = TagRef("tag", "school uniform")
    private val artist = TagRef("artist", "somebody")

    @Test
    fun libraryRoundTripsThroughJsonIncludingNamesWithQuotesAndUnicode() {
        val library = TagLibrary()
            .toggleFavorite(uniform)
            .createCollection("Любимое \"тихое\"")
            .toggleInCollection("Любимое \"тихое\"", artist)

        assertEquals(library, TagLibrary.fromJson(library.toJson()))
    }

    @Test
    fun favoriteTogglesOnAndOffWithoutDuplicates() {
        val once = TagLibrary().toggleFavorite(uniform)
        assertTrue(once.isFavorite(uniform))
        assertEquals(listOf(uniform), once.favorites)
        assertFalse(once.toggleFavorite(uniform).isFavorite(uniform))
    }

    @Test
    fun collectionNamesAreUniqueIgnoringCaseAndBlankNamesAreRejected() {
        val library = TagLibrary().createCollection("Reading")

        assertEquals(library, library.createCollection("reading "))
        assertEquals(library, library.createCollection("   "))
        assertEquals(2, library.createCollection("Other").collections.size)
    }

    @Test
    fun renameKeepsTagsAndRefusesAClashWithAnotherCollection() {
        val library = TagLibrary().createCollection("A").createCollection("B").toggleInCollection("A", uniform)

        val renamed = library.renameCollection("A", "C")
        assertEquals(listOf(uniform), renamed.collections.first { it.name == "C" }.tags)
        assertEquals(library, library.renameCollection("A", "b"))
        assertEquals(library, library.renameCollection("A", " "))
    }

    @Test
    fun deletingACollectionLeavesFavoritesAlone() {
        val library = TagLibrary().toggleFavorite(uniform).createCollection("A").toggleInCollection("A", uniform)

        val after = library.deleteCollection("A")
        assertTrue(after.collections.isEmpty())
        assertEquals(listOf(uniform), after.favorites)
    }

    @Test
    fun unreadableStoredDataBecomesAnEmptyLibrary() {
        assertEquals(TagLibrary(), TagLibrary.fromJson("not json"))
        assertEquals(TagLibrary(), TagLibrary.fromJson(null))
        assertEquals(TagLibrary(), TagLibrary.fromJson("""{"favorites":[{"type":"","name":""}],"collections":[{"name":" "}]}"""))
    }

    @Test
    fun selectionCyclesIncludeExcludeRemoveInTheOrderOfTheFilterSheet() {
        val included = emptyList<TagFilter>().cycled("tag", "a")
        assertEquals(listOf(TagFilter("tag", "a", TagMode.Include)), included)
        val excluded = included.cycled("tag", "a")
        assertEquals(listOf(TagFilter("tag", "a", TagMode.Exclude)), excluded)
        assertEquals(emptyList<TagFilter>(), excluded.cycled("tag", "a"))
    }

    @Test
    fun cyclingOneTagKeepsTheOthersAndTheSameNameOfAnotherType() {
        val selected = listOf(TagFilter("artist", "a", TagMode.Include))

        val next = selected.cycled("tag", "a")
        assertEquals(2, next.size)
        assertTrue(TagFilter("artist", "a", TagMode.Include) in next)
    }
}
