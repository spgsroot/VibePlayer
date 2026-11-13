package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
