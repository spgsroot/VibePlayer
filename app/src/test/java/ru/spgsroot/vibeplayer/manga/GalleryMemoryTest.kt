package ru.spgsroot.vibeplayer.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryMemoryTest {
    @Test
    fun tagsSurviveTheRoundTripIncludingAMissingCount() {
        val tags = listOf(GalleryTag(1, "tag", "big breasts", 241262), GalleryTag(2, "artist", "someone", null))
        assertEquals(tags, TagCodec.decode(TagCodec.encode(tags)))
    }

    @Test
    fun separatorsInsideANameCannotCorruptTheList() {
        val decoded = TagCodec.decode(TagCodec.encode(listOf(GalleryTag(1, "tag", "a\tb\nc", 5), GalleryTag(2, "tag", "d", 6))))
        assertEquals(listOf("a b c", "d"), decoded.map { it.name })
        assertEquals(listOf(5, 6), decoded.map { it.count })
    }

    @Test
    fun damagedLinesAreSkippedNotFatal() {
        val decoded = TagCodec.decode("1\ttag\tok\t3\nnot a line\nx\ttag\tbad id\t1\n2\t\tno type\t1\n3\ttag\tlast\t")
        assertEquals(listOf("ok", "last"), decoded.map { it.name })
        assertNull(decoded.last().count)
    }

    @Test
    fun anEmptyStringDecodesToNoTags() {
        assertEquals(emptyList<GalleryTag>(), TagCodec.decode(""))
    }

    @Test
    fun languageIgnoresTranslated() {
        val tags = listOf(GalleryTag(1, "language", "translated"), GalleryTag(2, "language", "chinese"))
        assertEquals("chinese", tags.language())
        assertNull(listOf(GalleryTag(1, "language", "translated")).language())
    }
}
