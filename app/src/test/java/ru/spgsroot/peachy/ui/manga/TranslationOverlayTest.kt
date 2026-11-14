package ru.spgsroot.peachy.ui.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationOverlayTest {
    @Test
    fun widePageViewLeavesBarsAtTheSides() {
        // A 0.5 aspect page in a 1000 x 1000 view is 500 wide, centred.
        val rect = fittedPage(1000f, 1000f, 0.5f)
        assertEquals(250f, rect.left, 0.01f)
        assertEquals(750f, rect.right, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(1000f, rect.bottom, 0.01f)
    }

    @Test
    fun tallPageViewLeavesBarsAboveAndBelow() {
        // A 2.0 aspect page in a 1000 x 1000 view is 500 tall, centred.
        val rect = fittedPage(1000f, 1000f, 2f)
        assertEquals(250f, rect.top, 0.01f)
        assertEquals(750f, rect.bottom, 0.01f)
        assertEquals(1000f, rect.width, 0.01f)
    }

    @Test
    fun pageWithTheViewsOwnAspectFillsIt() {
        val rect = fittedPage(800f, 400f, 2f)
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(800f, rect.width, 0.01f)
        assertEquals(400f, rect.height, 0.01f)
    }
}
