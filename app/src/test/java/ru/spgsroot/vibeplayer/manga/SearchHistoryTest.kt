package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryTest {
    @Test
    fun blankQueryIsIgnored() {
        val current = listOf("english", "school")
        assertEquals(current, rememberQuery(current, ""))
        assertEquals(current, rememberQuery(current, " \t\n "))
    }

    @Test
    fun newQueryMovesToTheFrontWithoutChangingTheInput() {
        val current = mutableListOf("english", "school")
        assertEquals(listOf("new", "english", "school"), rememberQuery(current, "new"))
        assertEquals(listOf("english", "school"), current)
    }

    @Test
    fun repeatedQueryMovesToTheFrontWithoutADuplicate() {
        assertEquals(
            listOf("school", "english", "japanese"),
            rememberQuery(listOf("english", "school", "japanese"), "school")
        )
    }

    @Test
    fun queryIsTrimmedBeforeRememberingAndMatchingARepeat() {
        assertEquals(listOf("school", "english"), rememberQuery(listOf("english", "school"), " school "))
    }

    @Test
    fun eleventhDistinctQueryDropsTheOldest() {
        val current = listOf("ten", "nine", "eight", "seven", "six", "five", "four", "three", "two", "one")
        assertEquals(
            listOf("eleven", "ten", "nine", "eight", "seven", "six", "five", "four", "three", "two"),
            rememberQuery(current, "eleven")
        )
    }

    @Test
    fun paddedNumericQueryIsAGalleryId() {
        assertTrue(isGalleryId(" 1225 "))
    }

    @Test
    fun mixedNumericAndTextQueryIsNotAGalleryId() {
        assertFalse(isGalleryId("12a"))
    }

    @Test
    fun emptyOrBlankQueryIsNotAGalleryId() {
        assertFalse(isGalleryId(""))
        assertFalse(isGalleryId(" \t "))
    }

    @Test
    fun signsAndEmbeddedSpacesAreNotGalleryIdDigits() {
        assertFalse(isGalleryId("+1225"))
        assertFalse(isGalleryId("-1225"))
        assertFalse(isGalleryId("12 25"))
    }
}
