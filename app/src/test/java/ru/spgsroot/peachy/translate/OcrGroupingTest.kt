package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGroupingTest {
    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        RawOcrBlock(listOf(RawOcrLine(text, PixelRect(left, top, right, bottom))), PixelRect(left, top, right, bottom))

    @Test fun mangaRowsReadTopFirstAndRightToLeftDespiteJitter() {
        val input = listOf(
            block("下左", 100, 650, 300, 850), block("上左", 100, 100, 300, 300),
            block("下右", 650, 620, 850, 820), block("上右", 650, 130, 850, 330)
        )
        for (source in listOf("ja", "zh")) {
            assertEquals(listOf("上右", "上左", "下右", "下左"), groupOcrBlocks(input, 1000, 1000, source).map { it.text })
        }
    }

    @Test fun latinAndKoreanReadRowsLeftToRight() {
        val input = listOf(block("bottom", 50, 700, 200, 800), block("right", 600, 90, 900, 220), block("left", 50, 100, 250, 230))
        for (source in listOf("en", "ko")) {
            assertEquals(listOf("left", "right", "bottom"), groupOcrBlocks(input, 1000, 1000, source).map { it.text })
        }
    }

    @Test fun autoUsesJapaneseRecognizerButKeepsDefaultLeftToRightOrdering() {
        val input = listOf(block("right", 600, 90, 900, 220), block("left", 50, 100, 250, 230))
        assertEquals(listOf("left", "right"), groupOcrBlocks(input, 1000, 1000, "auto").map { it.text })
    }

    @Test fun autoKeepsSpacesBetweenLines() {
        val rect = PixelRect(10, 10, 90, 90)
        val input = listOf(RawOcrBlock(listOf(
            RawOcrLine("hello", PixelRect(10, 10, 90, 30)),
            RawOcrLine("world", PixelRect(10, 40, 90, 60))
        ), rect))
        assertEquals("hello world", groupOcrBlocks(input, 100, 100, "auto").single().text)
    }

    @Test fun nonOverlappingBubblesStayInSeparateRowsEvenOnTallPage() {
        val input = listOf(block("first", 10, 10, 110, 70), block("second", 700, 100, 800, 160))
        assertEquals(listOf("first", "second"), groupOcrBlocks(input, 1000, 3000, "ja").map { it.text })
    }

    @Test fun verticalColumnsReadRightToLeftAndTopToBottom() {
        val rect = PixelRect(100, 100, 900, 600)
        val lines = listOf(
            RawOcrLine("左", PixelRect(100, 100, 180, 500)),
            RawOcrLine("下", PixelRect(800, 350, 880, 600)),
            RawOcrLine("右", PixelRect(805, 100, 885, 300))
        )
        assertEquals("右下左", groupOcrBlocks(listOf(RawOcrBlock(lines, rect)), 1000, 1000, "ja").single().text)
    }

    @Test fun whitespaceAndSymbolOnlyNoiseAreDroppedButSingleLettersRemain() {
        val input = listOf("  ", "!", "…", "※", "!?", " A ", " 字 ").mapIndexed { i, text -> block(text, 10, i * 100, 90, i * 100 + 80) }
        assertEquals(listOf("A", "字"), groupOcrBlocks(input, 1000, 1000, "en").map { it.text })
    }

    @Test fun boxesAreClampedAndNormalizedUsingEachImageDimension() {
        val result = groupOcrBlocks(listOf(block("text", -10, 50, 1200, 700)), 1000, 500, "en").single()
        assertEquals(Box(0f, 0.1f, 1f, 1f), result.box)
    }

    @Test fun zeroAreaOrFullyOffPageBoxesAreDropped() {
        val input = listOf(block("outside", -100, 10, -10, 80), block("flat", 20, 20, 20, 80), block("inverted", 90, 80, 10, 20))
        assertTrue(groupOcrBlocks(input, 1000, 1000, "ja").isEmpty())
    }

    @Test fun emptyInputReturnsEmptyOutput() {
        assertTrue(groupOcrBlocks(emptyList(), 1000, 1000, "en").isEmpty())
    }

    @Test fun japaneseAndChineseJoinTrimmedLinesWithoutSpaces() {
        val input = listOf(RawOcrBlock(listOf(RawOcrLine(" 你好 ", PixelRect(10, 10, 90, 30)), RawOcrLine(" 世界 ", PixelRect(10, 40, 90, 60))), PixelRect(10, 10, 90, 60)))
        for (source in listOf("ja", "zh")) assertEquals("你好世界", groupOcrBlocks(input, 100, 100, source).single().text)
    }

    @Test fun latinAndKoreanJoinLinesWithSpaces() {
        val input = listOf(RawOcrBlock(listOf(RawOcrLine(" hello ", PixelRect(10, 10, 90, 30)), RawOcrLine(" world ", PixelRect(10, 40, 90, 60))), PixelRect(10, 10, 90, 60)))
        for (source in listOf("en", "ko")) assertEquals("hello world", groupOcrBlocks(input, 100, 100, source).single().text)
    }

    @Test fun lowConfidenceLinesAreDroppedAndThresholdIsInclusive() {
        val rect = PixelRect(10, 10, 90, 90)
        val input = listOf(RawOcrBlock(listOf(RawOcrLine("bad", rect, 0.29f), RawOcrLine("good", rect, 0.3f)), rect))
        assertEquals("good", groupOcrBlocks(input, 100, 100, "en").single().text)
    }

    @Test fun filteringEveryLineDropsTheBlock() {
        val rect = PixelRect(10, 10, 90, 90)
        assertTrue(groupOcrBlocks(listOf(RawOcrBlock(listOf(RawOcrLine("noise", rect, 0.1f)), rect)), 100, 100, "en").isEmpty())
    }
}
