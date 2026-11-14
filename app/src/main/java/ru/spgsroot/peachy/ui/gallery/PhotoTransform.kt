package ru.spgsroot.peachy.ui.gallery

/** Translation is relative to the window center; focal coordinates use that same origin. */
internal data class PhotoTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun change(
        zoom: Float, panX: Float, panY: Float, focalX: Float, focalY: Float,
        windowWidth: Float, windowHeight: Float, fittedWidth: Float, fittedHeight: Float
    ): PhotoTransform {
        val nextScale = (scale * zoom).coerceIn(1f, 5f)
        val ratio = nextScale / scale
        val maxX = ((fittedWidth * nextScale - windowWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((fittedHeight * nextScale - windowHeight) / 2f).coerceAtLeast(0f)
        return PhotoTransform(
            nextScale,
            (focalX - (focalX - x) * ratio + panX).coerceIn(-maxX, maxX),
            (focalY - (focalY - y) * ratio + panY).coerceIn(-maxY, maxY)
        )
    }
}
