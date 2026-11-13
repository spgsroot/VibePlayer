package ru.spgsroot.peachy.ui.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import ru.spgsroot.peachy.manga.MAX_ZOOM
import ru.spgsroot.peachy.manga.MIN_ZOOM
import ru.spgsroot.peachy.manga.clampPan
import ru.spgsroot.peachy.manga.doubleTapZoom
import ru.spgsroot.peachy.manga.panForZoomAt
import ru.spgsroot.peachy.manga.stepZoom

/** Zoom and pan of the page on screen; the reader keeps one and only the active page reads it. */
class ReaderZoom {
    var zoom by mutableFloatStateOf(MIN_ZOOM)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set

    /** Size of the active page's frame, set by that page; needed to keep the pan inside it. */
    var width = 0f
    var height = 0f

    val zoomed: Boolean get() = zoom > MIN_ZOOM + 0.01f

    fun reset() {
        zoom = MIN_ZOOM
        pan = Offset.Zero
    }

    /** Pinch or drag: [zoomChange] multiplies, [panChange] moves. */
    fun transform(zoomChange: Float, panChange: Offset) {
        val next = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoom = next
        pan = if (next <= MIN_ZOOM) Offset.Zero else clamp(pan + panChange, next)
    }

    fun doubleTap(at: Offset) {
        val next = doubleTapZoom(zoom)
        if (next <= MIN_ZOOM) {
            reset()
        } else {
            zoom = next
            pan = Offset(panForZoomAt(at.x, width, next), panForZoomAt(at.y, height, next))
        }
    }

    /** Zoom button: zooms about the middle of the frame. */
    fun step(direction: Int) {
        val next = stepZoom(zoom, direction)
        zoom = next
        pan = if (next <= MIN_ZOOM) Offset.Zero else clamp(pan, next)
    }

    private fun clamp(value: Offset, zoom: Float) =
        Offset(clampPan(value.x, zoom, width), clampPan(value.y, zoom, height))
}
