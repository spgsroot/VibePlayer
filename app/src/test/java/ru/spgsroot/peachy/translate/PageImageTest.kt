package ru.spgsroot.peachy.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class PageImageTest {
    @Test
    fun smallImageIsNotUpscaled() {
        assertEquals(800 to 1200, PageImage.scaledSize(800, 1200))
    }

    @Test
    fun longSideIsClamped() {
        assertEquals(1058 to 1500, PageImage.scaledSize(1058, 1500))
        assertEquals(1067 to 1600, PageImage.scaledSize(3200, 4800))
    }

    @Test
    fun aspectRatioIsKept() {
        assertEquals(1600 to 160, PageImage.scaledSize(3000, 300))
    }

    @Test
    fun tallStripKeepsWidthProportion() {
        assertEquals(107 to 1600, PageImage.scaledSize(800, 12000))
    }

    @Test
    fun sampleSizeIsPowerOfTwoAndNeverBelowTarget() {
        assertEquals(2, PageImage.sampleSize(3200, 4800))
        assertEquals(1, PageImage.sampleSize(800, 1200))
        assertEquals(4, PageImage.sampleSize(10000, 10000))
        assertEquals(1, PageImage.sampleSize(1600, 1000))
    }
}
