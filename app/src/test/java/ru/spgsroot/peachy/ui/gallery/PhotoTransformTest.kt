package ru.spgsroot.peachy.ui.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoTransformTest {
    @Test
    fun zoomIsClampedAndReturningToFitResetsPan() {
        val zoomed = PhotoTransform().change(20f, 500f, 500f, 0f, 0f, 400f, 300f, 400f, 300f)
        assertEquals(5f, zoomed.scale, 0f)
        val reset = zoomed.change(0.01f, 20f, 20f, 0f, 0f, 400f, 300f, 400f, 300f)
        assertEquals(PhotoTransform(), reset)
    }

    @Test
    fun panBoundsUseFittedImageRatherThanLetterboxedWindow() {
        val moved = PhotoTransform().change(2f, 900f, -900f, 0f, 0f, 400f, 300f, 200f, 300f)
        assertEquals(0f, moved.x, 0f)
        assertEquals(-150f, moved.y, 0f)
    }

    @Test
    fun pinchKeepsThePointUnderTheFingersStationary() {
        val moved = PhotoTransform().change(2f, 0f, 0f, 50f, 30f, 400f, 300f, 400f, 300f)
        assertEquals(-50f, moved.x, 0f)
        assertEquals(-30f, moved.y, 0f)
    }
}
