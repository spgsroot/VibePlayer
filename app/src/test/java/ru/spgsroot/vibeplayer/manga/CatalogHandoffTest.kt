package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.spgsroot.vibeplayer.ui.manga.decodeFilters
import ru.spgsroot.vibeplayer.ui.manga.encodeFilter

class CatalogHandoffTest {
    @Test
    fun filtersSurviveTheSavedStateRoundTripIncludingSeparatorsInNames() {
        val filters = listOf(
            TagFilter("tag", "school uniform", TagMode.Include),
            TagFilter("artist", "a|b", TagMode.Exclude)
        )

        assertEquals(filters, decodeFilters(filters.map(::encodeFilter)))
    }

    @Test
    fun corruptEntriesAreDroppedInsteadOfBecomingEmptyFilters() {
        val decoded = decodeFilters(listOf("Include|tag|ok", "Bogus|tag|x", "Include|tag", "Include||x", "Exclude|artist|"))

        assertEquals(listOf(TagFilter("tag", "ok", TagMode.Include)), decoded)
    }
}
