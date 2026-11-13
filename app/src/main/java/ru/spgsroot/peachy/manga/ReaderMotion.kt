package ru.spgsroot.peachy.manga

import kotlin.math.abs

class PageTurnGate {
    private var initialized = false
    private var lastIdlePage = 0

    fun onPage(page: Int, idle: Boolean): Boolean {
        if (!idle) return false
        val changed = initialized && page != lastIdlePage
        lastIdlePage = page
        initialized = true
        return changed
    }
}

fun centerPage(centers: List<Int>, viewportCenter: Int): Int {
    var nearest = -1
    var nearestDistance = Long.MAX_VALUE
    for (index in centers.indices) {
        val distance = abs(centers[index].toLong() - viewportCenter)
        if (distance < nearestDistance) {
            nearest = index
            nearestDistance = distance
        }
    }
    return nearest
}

enum class TapZone { Back, Chrome, Forward }

fun tapZone(x: Float, width: Float, mirror: Boolean): TapZone {
    val zone = when {
        x < width / 3 -> TapZone.Back
        x > width * 2 / 3 -> TapZone.Forward
        else -> TapZone.Chrome
    }
    if (!mirror) return zone
    return when (zone) {
        TapZone.Back -> TapZone.Forward
        TapZone.Forward -> TapZone.Back
        TapZone.Chrome -> TapZone.Chrome
    }
}

fun startPage(saved: Int?, pageCount: Int): Int {
    return if (saved != null && saved >= 0 && saved < pageCount) saved else 0
}

const val MIN_ZOOM = 1f
const val MAX_ZOOM = 6f
const val DOUBLE_TAP_ZOOM = 2.5f
const val ZOOM_BUTTON_STEP = 0.75f

/** The page cannot be dragged further than the zoomed image overhangs its frame. */
fun clampPan(pan: Float, zoom: Float, extent: Float): Float {
    val limit = extent * (zoom - 1f) / 2f
    return if (limit <= 0f) 0f else pan.coerceIn(-limit, limit)
}

/** Pan that brings [tap] to the middle of a frame [extent] wide when the zoom becomes [zoom]. */
fun panForZoomAt(tap: Float, extent: Float, zoom: Float): Float =
    clampPan((extent / 2f - tap) * (zoom - 1f), zoom, extent)

/** Double tap zooms in on the tapped point; when already zoomed it goes back to the full page. */
fun doubleTapZoom(current: Float): Float = if (current > MIN_ZOOM + 0.05f) MIN_ZOOM else DOUBLE_TAP_ZOOM

fun stepZoom(current: Float, direction: Int): Float =
    (current + ZOOM_BUTTON_STEP * direction).coerceIn(MIN_ZOOM, MAX_ZOOM)
