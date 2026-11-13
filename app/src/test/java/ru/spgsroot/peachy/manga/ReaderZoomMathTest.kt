package ru.spgsroot.peachy.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderZoomMathTest {
    @Test
    fun unzoomedPageCannotBePanned() {
        assertEquals(0f, clampPan(120f, MIN_ZOOM, 1000f), 0f)
    }

    @Test
    fun panStopsWhereTheImageEdgeMeetsTheFrame() {
        // 2x zoom makes a 1000 px frame 2000 px wide: 1000 px overhang, 500 px on each side.
        assertEquals(500f, clampPan(900f, 2f, 1000f), 0.001f)
        assertEquals(-500f, clampPan(-900f, 2f, 1000f), 0.001f)
        assertEquals(40f, clampPan(40f, 2f, 1000f), 0.001f)
    }

    @Test
    fun doubleTapOnTheEdgeStaysInsideTheFrame() {
        val zoom = DOUBLE_TAP_ZOOM
        val limit = 1000f * (zoom - 1f) / 2f
        assertEquals(limit, panForZoomAt(0f, 1000f, zoom), 0.001f)
        assertEquals(-limit, panForZoomAt(1000f, 1000f, zoom), 0.001f)
    }

    @Test
    fun doubleTapOnTheMiddleDoesNotShiftThePage() {
        assertEquals(0f, panForZoomAt(500f, 1000f, DOUBLE_TAP_ZOOM), 0.001f)
    }

    @Test
    fun doubleTapZoomsInThenBackOut() {
        assertEquals(DOUBLE_TAP_ZOOM, doubleTapZoom(MIN_ZOOM), 0f)
        assertEquals(MIN_ZOOM, doubleTapZoom(DOUBLE_TAP_ZOOM), 0f)
        assertEquals(MIN_ZOOM, doubleTapZoom(MAX_ZOOM), 0f)
    }

    @Test
    fun zoomButtonsStayWithinBounds() {
        assertEquals(MIN_ZOOM, stepZoom(MIN_ZOOM, -1), 0f)
        assertEquals(MAX_ZOOM, stepZoom(MAX_ZOOM, 1), 0f)
        assertEquals(MIN_ZOOM + ZOOM_BUTTON_STEP, stepZoom(MIN_ZOOM, 1), 0.001f)
    }
}
