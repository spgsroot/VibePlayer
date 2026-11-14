package ru.spgsroot.peachy.ui.gallery

import android.content.SharedPreferences
import kotlin.math.roundToInt

/** Gallery presentation. [key] is persisted, so existing layout choices survive upgrades. */
enum class GalleryLayout(val key: String) {
    /** Tall portrait covers with the title underneath, like the manga catalog. */
    Large("large"),
    /** Adjustable 16:9 grid; retains the historic "two" preference key. */
    Two("two"),
    /** One 16:9 card across the whole width. */
    One("one"),
    /** One row per item: a small preview, title, metadata and the menu. */
    List("list")
}

object GalleryLayoutStore {
    private const val LAYOUT = "layout"
    private const val GRID_COLUMNS = "grid_columns"
    val DEFAULT = GalleryLayout.Two

    fun load(prefs: SharedPreferences): GalleryLayout {
        val stored = prefs.getString(LAYOUT, null)
        return GalleryLayout.entries.firstOrNull { it.key == stored } ?: DEFAULT
    }

    fun save(prefs: SharedPreferences, layout: GalleryLayout) {
        prefs.edit().putString(LAYOUT, layout.key).apply()
    }

    fun loadGridColumns(prefs: SharedPreferences): Int =
        prefs.getInt(GRID_COLUMNS, 2).coerceIn(1, 5)

    fun saveGridColumns(prefs: SharedPreferences, columns: Int) {
        prefs.edit().putInt(GRID_COLUMNS, columns.coerceIn(1, 5)).apply()
    }
}

/**
 * Columns are a discrete user choice, not an adaptive minimum width. Both grids divide the
 * available width equally after 16dp outer padding per side and 12dp between columns:
 * tileWidth = (availableWidth - 32dp - 12dp * (columns - 1)) / columns.
 * One/List force one column without overwriting the separately persisted grid choice.
 */
object GallerySizing {
    fun gridColumns(sliderValue: Float): Int = sliderValue.coerceIn(1f, 5f).roundToInt()

    fun columns(layout: GalleryLayout, gridColumns: Int): Int = when (layout) {
        GalleryLayout.One, GalleryLayout.List -> 1
        GalleryLayout.Two, GalleryLayout.Large -> gridColumns.coerceIn(1, 5)
    }
}
