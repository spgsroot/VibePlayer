package ru.spgsroot.vibeplayer.manga

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagBlacklistTest {
    @Test
    fun matchingTagIdBlocksTheGallery() {
        assertTrue(blocks(listOf(7, 9), emptyList(), setOf(9), emptySet()))
    }

    @Test
    fun tagNameMatchesTheLowercaseBlacklistExactly() {
        assertTrue(blocks(emptyList(), listOf("English"), emptySet(), setOf("english")))
    }

    @Test
    fun nonOverlappingTagsDoNotBlockTheGallery() {
        assertFalse(blocks(listOf(7), listOf("Japanese"), setOf(9), setOf("english")))
    }

    @Test
    fun nameSubstringsDoNotBlockTheGallery() {
        assertFalse(blocks(emptyList(), listOf("English translation"), emptySet(), setOf("english")))
        assertFalse(blocks(emptyList(), listOf("English"), emptySet(), setOf("eng")))
    }

    @Test
    fun unknownTagsDoNotMatchEvenWhenTheBlacklistContainsATitleWord() {
        assertFalse(blocks(emptyList(), emptyList(), setOf(9), setOf("english")))
    }

    @Test
    fun emptyBlacklistDoesNotBlockKnownTags() {
        assertFalse(blocks(listOf(9), listOf("English"), emptySet(), emptySet()))
    }

    @Test
    fun searchCardsUseKnownNamesForExactBlockingAndReappearAfterRemoval() = runBlocking {
        val card = GallerySummary(1, "English title is not tag metadata", "", 1, listOf(7))
        val cards = listOf(card)
        suspend fun visible(knownNames: Map<Int, String>, blockedNames: Set<String>) =
            filterSearchResults(cards, knownNames) { ids, names ->
                blocks(ids, names, emptySet(), blockedNames)
            }

        assertEquals(cards, visible(emptyMap(), setOf("english")))
        assertEquals(cards, visible(mapOf(9 to "english"), setOf("english")))
        assertEquals(emptyList<GallerySummary>(), visible(mapOf(7 to "english"), setOf("EnGLisH")))
        assertEquals(cards, visible(mapOf(7 to "english translation"), setOf("english")))
        assertEquals(cards, visible(mapOf(7 to "english"), setOf("eng")))
        assertEquals(cards, visible(mapOf(7 to "english"), emptySet()))
    }
}
