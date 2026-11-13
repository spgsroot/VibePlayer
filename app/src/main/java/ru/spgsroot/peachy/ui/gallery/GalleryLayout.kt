package ru.spgsroot.peachy.ui.gallery

import android.content.SharedPreferences

/** How the gallery lays its videos out. [key] is what gets stored, so reordering the entries is safe. */
enum class GalleryLayout(val key: String) {
    /** Tall portrait covers with the title underneath, like the manga catalog. */
    Large("large"),
    /** Two 16:9 cards per row: the gallery as it always was. */
    Two("two"),
    /** One 16:9 card across the whole width. */
    One("one"),
    /** One row per video: a small preview, title, length and the menu. */
    List("list")
}

object GalleryLayoutStore {
    private const val LAYOUT = "layout"
    val DEFAULT = GalleryLayout.Two

    fun load(prefs: SharedPreferences): GalleryLayout {
        val stored = prefs.getString(LAYOUT, null)
        return GalleryLayout.entries.firstOrNull { it.key == stored } ?: DEFAULT
    }

    fun save(prefs: SharedPreferences, layout: GalleryLayout) {
        prefs.edit().putString(LAYOUT, layout.key).apply()
    }
}
