package ru.spgsroot.vibeplayer.manga

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
}
