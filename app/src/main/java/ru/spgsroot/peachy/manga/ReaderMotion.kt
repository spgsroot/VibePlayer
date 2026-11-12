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
