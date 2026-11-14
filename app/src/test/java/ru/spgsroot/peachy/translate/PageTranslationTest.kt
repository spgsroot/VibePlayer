package ru.spgsroot.peachy.translate

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.spgsroot.peachy.data.db.PageTranslationDao
import ru.spgsroot.peachy.data.db.PageTranslationEntity

class PageTranslationTest {
    private val plain = """
        {"sourceLanguage":"ja","lines":[
          {"kind":"speech","text":"ハ？","translation":"А?"},
          {"kind":"thought","text":"まさか","translation":"Неужели"}
        ]}
    """.trimIndent()

    @Test
    fun plainJsonParses() {
        val page = PageTranslation.parse(plain)!!
        assertEquals("ja", page.sourceLanguage)
        assertEquals(
            listOf(
                TranslatedLine(LineKind.Speech, "ハ？", "А?"),
                TranslatedLine(LineKind.Thought, "まさか", "Неужели")
            ),
            page.lines
        )
    }

    @Test
    fun markdownFenceIsStripped() {
        val page = PageTranslation.parse("```json\n$plain\n```")
        assertEquals(2, page!!.lines.size)
    }

    @Test
    fun proseAroundObjectIsIgnored() {
        val page = PageTranslation.parse("Here you go:\n$plain\nHope that helps!")
        assertEquals(2, page!!.lines.size)
    }

    @Test
    fun unknownKindBecomesSpeech() {
        val page = PageTranslation.parse("""{"sourceLanguage":"ja","lines":[{"kind":"shout","text":"a","translation":"b"}]}""")
        assertEquals(LineKind.Speech, page!!.lines.single().kind)
    }

    @Test
    fun lineWithoutTranslationIsDropped() {
        val page = PageTranslation.parse(
            """{"sourceLanguage":"ja","lines":[
              {"kind":"speech","text":"a","translation":"  "},
              {"kind":"sfx","text":"","translation":"БАМ"}
            ]}"""
        )
        assertEquals(listOf(TranslatedLine(LineKind.Sfx, "", "БАМ")), page!!.lines)
    }

    @Test
    fun emptyLinesIsValid() {
        val page = PageTranslation.parse("""{"sourceLanguage":"ja","lines":[]}""")
        assertNotNull(page)
        assertTrue(page!!.lines.isEmpty())
    }

    @Test
    fun cutOffReplyIsNull() {
        assertNull(PageTranslation.parse("""{"sourceLanguage":"ja","lines":[{"kind":"speech","te"""))
    }

    @Test
    fun garbageIsNull() {
        assertNull(PageTranslation.parse("I cannot help with that."))
    }

    @Test
    fun roundTrip() {
        val page = PageTranslation(
            "ja",
            listOf(
                TranslatedLine(LineKind.Narration, "夜", "Ночь"),
                TranslatedLine(LineKind.Sign, "", "Отель")
            )
        )
        assertEquals(page, PageTranslation.parse(page.toJson()))
    }

    @Test
    fun normalizedBoxCoordinatesArePreserved() {
        assertEquals(Box(0.25f, 0.125f, 0.75f, 0.875f), lineWithBox("[0.25,0.125,0.75,0.875]").box)
        assertEquals(Box(0f, 0f, 1f, 1f), lineWithBox("[0,0,1,1]").box)
    }

    @Test
    fun thousandScaleCoordinatesIncludeFractionalNumbers() {
        assertEquals(Box(0.1f, 0.2f, 0.9f, 0.8f), lineWithBox("[100,200,900,800]").box)
        assertEquals(
            Box(0.1005f, 0.20025f, 0.90075f, 0.8005f),
            lineWithBox("[100.5,200.25,900.75,800.5]").box
        )
    }

    @Test
    fun anyCoordinateAboveOneScalesEveryCoordinate() {
        assertEquals(Box(0.0005f, 0.00025f, 1f, 0.5f), lineWithBox("[0.5,0.25,1000,500]").box)
    }

    @Test
    fun coordinatesAreClampedAfterScaling() {
        assertEquals(Box(0f, 0f, 1f, 1f), lineWithBox("[-100,-200,1200,1400]").box)
        assertEquals(Box(0f, 0f, 0.75f, 1f), lineWithBox("[-0.5,-0.25,0.75,1]").box)
    }

    @Test
    fun reversedCoordinatesAreSwappedOnBothAxes() {
        assertEquals(Box(0.2f, 0.1f, 0.8f, 0.9f), lineWithBox("[0.8,0.9,0.2,0.1]").box)
        assertEquals(Box(0.2f, 0.1f, 0.8f, 0.9f), lineWithBox("[800,900,200,100]").box)
    }

    @Test
    fun malformedBoxIsDroppedWithoutDroppingEitherLine() {
        val invalid = listOf(
            "null", "{}", "true", "\"0,0,1,1\"", "[]", "[0,0,1]", "[0,0,1,1,2]",
            "[\"0\",0,1,1]", "[0,0,\"1\",1]", "[false,0,1,1]", "[null,0,1,1]",
            "[{},0,1,1]", "[[],0,1,1]", "[NaN,0,1,1]", "[0,Infinity,1,1]",
            "[0,0,1e309,1]", "[0,0,1,-1e309]"
        )
        for (box in invalid) {
            val page = PageTranslation.parse(
                """{"sourceLanguage":"ja","lines":[
                  {"kind":"sign","text":"原文","translation":"Translated","box":$box},
                  {"kind":"speech","text":"next","translation":"Next","box":[0,0,1,1]}
                ]}"""
            )
            assertNotNull("box=$box must not invalidate the page", page)
            assertEquals("box=$box must not drop the line", 2, page!!.lines.size)
            assertEquals(TranslatedLine(LineKind.Sign, "原文", "Translated"), page.lines.first())
            assertEquals(Box(0f, 0f, 1f, 1f), page.lines.last().box)
        }
    }

    @Test
    fun missingBoxKeepsLegacyLine() {
        assertNull(PageTranslation.parse(plain)!!.lines.first().box)
        assertNull(TranslatedLine(LineKind.Speech, "original", "translated").box)
    }

    @Test
    fun degenerateBoxIsDroppedIncludingAfterClamping() {
        for (box in listOf("[0.5,0,0.5,1]", "[0,0.5,1,0.5]", "[0,0,0,0]", "[-0.5,0,-0.1,1]", "[0,1200,1000,1500]")) {
            assertNull("box=$box", lineWithBox(box).box)
        }
    }

    @Test
    fun storedJsonContainsFractionalNumbersInLeftTopRightBottomOrder() {
        val page = PageTranslation("ja", listOf(lineWithBox("[100,200,900,800]")))
        val box = JSONObject(page.toJson()).getJSONArray("lines").getJSONObject(0).getJSONArray("box")
        assertEquals(4, box.length())
        for ((index, expected) in listOf(0.1, 0.2, 0.9, 0.8).withIndex()) {
            assertTrue(box.get(index) is Number)
            assertEquals(expected, box.getDouble(index), 0.000001)
        }
        assertEquals(page, PageTranslation.parse(page.toJson()))
    }

    @Test
    fun cachedTranslationRoundTripsBoxesAndLegacyLines() = runBlocking {
        val page = PageTranslation(
            "ja",
            listOf(
                TranslatedLine(LineKind.Speech, "原文", "Translated", Box(0.125f, 0.25f, 0.75f, 0.875f)),
                TranslatedLine(LineKind.Narration, "次", "Next")
            )
        )
        val store = TranslationStore(MapDao())
        store.put(1, 3, "ru", "model", page)
        assertEquals(page, store.get(1, 3, "ru"))
    }

    private fun lineWithBox(box: String): TranslatedLine = PageTranslation.parse(
        """{"sourceLanguage":"ja","lines":[{"kind":"speech","text":"original","translation":"translated","box":$box}]}"""
    )!!.lines.single()

    private class MapDao : PageTranslationDao {
        private val rows = LinkedHashMap<Triple<Int, Int, String>, PageTranslationEntity>()

        override suspend fun get(galleryId: Int, page: Int, lang: String) = rows[Triple(galleryId, page, lang)]
        override suspend fun upsert(entity: PageTranslationEntity) {
            rows[Triple(entity.galleryId, entity.page, entity.lang)] = entity
        }
        override suspend fun upsertAll(entities: List<PageTranslationEntity>) = entities.forEach { upsert(it) }
        override suspend fun getAll() = rows.values.toList()
        override suspend fun pagesDone(galleryId: Int, lang: String) =
            rows.values.filter { it.galleryId == galleryId && it.lang == lang }.map { it.page }
        override suspend fun deleteGallery(galleryId: Int) {
            rows.keys.removeAll { it.first == galleryId }
        }
    }
}
