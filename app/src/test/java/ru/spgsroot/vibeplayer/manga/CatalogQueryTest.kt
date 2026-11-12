package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CatalogQueryTest {
    @Test
    fun spacedIncludeAndExcludeAreQuotedAndSortStaysOut() {
        val query = CatalogQuery(
            text = "  school  ",
            tags = listOf(
                TagFilter("tag", "school uniform", TagMode.Include),
                TagFilter("artist", "name", TagMode.Exclude)
            ),
            sort = SortKey.PopularWeek
        )
        assertEquals("""school tag:"school uniform" -artist:name""", query.toSearchQuery())
        assertFalse(query.toSearchQuery().contains("sort="))
        assertEquals("popular-week", query.sort.toApi())
    }

    @Test
    fun datePresetsAndCustomRangeUseNhentaiUploadedSyntax() {
        assertEquals("uploaded:>7d", CatalogQuery(uploaded = "uploaded:>7d").toSearchQuery())
        val ranged = CatalogQuery(uploaded = "uploaded:>2024-01-01 uploaded:<2024-02-01")
        assertEquals("uploaded:>2024-01-01 uploaded:<2024-02-01", ranged.toSearchQuery())
    }

    @Test
    fun pagesDropZeroAndExactReplacesRange() {
        assertEquals("", CatalogQuery(pages = PagesFilter(min = 0, max = -1, exact = null)).toSearchQuery())
        assertEquals("pages:>10 pages:<40", CatalogQuery(pages = PagesFilter(10, 40, null)).toSearchQuery())
        assertEquals("pages:12", CatalogQuery(pages = PagesFilter(10, 40, 12)).toSearchQuery())
    }

    @Test
    fun tagCycleIsIncludeThenExcludeThenGone() {
        assertEquals(TagMode.Include, nextTagMode(null))
        assertEquals(TagMode.Exclude, nextTagMode(TagMode.Include))
        assertEquals(null, nextTagMode(TagMode.Exclude))
    }

    @Test
    fun allSortKeysUseSeparateApiValues() {
        assertEquals("date", SortKey.Date.toApi())
        assertEquals("popular", SortKey.Popular.toApi())
        assertEquals("popular-today", SortKey.PopularToday.toApi())
        assertEquals("popular-week", SortKey.PopularWeek.toApi())
        assertEquals("popular-month", SortKey.PopularMonth.toApi())
        for (sort in SortKey.entries) {
            assertEquals("school", CatalogQuery(text = "school", sort = sort).toSearchQuery())
        }
    }

    @Test
    fun emptyAndBlankPartsDoNotAddSeparators() {
        assertEquals("", CatalogQuery().toSearchQuery())
        assertEquals("", CatalogQuery(text = " \t\n", uploaded = " \t").toSearchQuery())
        assertEquals("pages:1", CatalogQuery(text = " ", pages = PagesFilter(exact = 1)).toSearchQuery())
        assertEquals("pages:>1", CatalogQuery(pages = PagesFilter(min = 1, max = 0)).toSearchQuery())
        assertEquals("pages:<1", CatalogQuery(pages = PagesFilter(min = -1, max = 1)).toSearchQuery())
    }

    @Test
    fun combinedPartsStayInTextTagUploadedPagesOrder() {
        val query = CatalogQuery(
            text = "  school  uniform  ",
            tags = listOf(TagFilter("language", "english", TagMode.Include)),
            uploaded = "uploaded:>7d",
            pages = PagesFilter(min = 10, max = 40)
        )
        assertEquals("school  uniform language:english uploaded:>7d pages:>10 pages:<40", query.toSearchQuery())
    }

    @Test
    fun excludedSpacedNamesAreQuotedWithoutOtherEscaping() {
        val query = CatalogQuery(
            tags = listOf(
                TagFilter("artist", "first last", TagMode.Exclude),
                TagFilter("tag", "a\"b\\c", TagMode.Include)
            )
        )
        assertEquals("-artist:\"first last\" tag:a\"b\\c", query.toSearchQuery())
    }

    @Test
    fun nonPositiveExactPagesAreOmittedWithoutRestoringRange() {
        assertEquals("", CatalogQuery(pages = PagesFilter(10, 40, 0)).toSearchQuery())
        assertEquals("", CatalogQuery(pages = PagesFilter(10, 40, -1)).toSearchQuery())
    }
}
