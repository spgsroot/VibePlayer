package ru.spgsroot.peachy.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TagNamesTest {
    @Test
    fun russianShowsTranslatedNamesPerType() {
        assertEquals("большая грудь", TagNames.display("tag", "big breasts", "ru"))
        assertEquals("додзинси", TagNames.display("category", "doujinshi", "ru"))
        assertEquals("японский", TagNames.display("language", "japanese", "ru"))
    }

    @Test
    fun otherLanguagesAndUnknownNamesKeepTheEnglishName() {
        assertEquals("big breasts", TagNames.display("tag", "big breasts", "en"))
        assertEquals("some brand new tag", TagNames.display("tag", "some brand new tag", "ru"))
    }

    @Test
    fun namesAreOnlyTranslatedForTheirOwnType() {
        // "manga" is both a tag and a category name, and "japanese" is only a language.
        assertEquals("Hito", TagNames.display("artist", "Hito", "ru"))
        assertEquals("japanese", TagNames.display("tag", "japanese", "ru"))
        assertEquals("манга", TagNames.display("category", "manga", "ru"))
    }

    @Test
    fun bundledDictionariesAreWellFormed() {
        // parse() rejects blank sides, a missing separator and repeated keys, so loading them is the check.
        listOf(TAGS_RU, CATEGORIES_RU, LANGUAGES_RU).forEach { raw ->
            val table = TagNames.parse(raw)
            assertTrue(table.isNotEmpty())
            // Keys are the API's names, which are lowercase and carry no padding.
            assertTrue(table.keys.all { it == it.trim() && it == it.lowercase() })
        }
    }

    @Test
    fun malformedOrRepeatedLinesAreRejected() {
        assertThrows(IllegalStateException::class.java) { TagNames.parse("a|б\na|в") }
        assertThrows(IllegalStateException::class.java) { TagNames.parse("a-without-separator") }
        assertThrows(IllegalStateException::class.java) { TagNames.parse("a|") }
    }
}
