package ru.spgsroot.peachy.ui.gallery

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryLayoutTest {
    /** A map-backed [SharedPreferences]; only the calls the store makes are supported. */
    private fun prefs(initial: Map<String, Any> = emptyMap()): SharedPreferences {
        val values = initial.toMutableMap()
        lateinit var editor: Any
        editor = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putString", "putInt" -> { values[args[0] as String] = args[1]!!; editor }
                "apply", "commit" -> true
                else -> error("unexpected ${method.name}")
            }
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString", "getInt" -> values[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> error("unexpected ${method.name}")
            }
        } as SharedPreferences
    }

    @Test
    fun freshInstallKeepsTheTwoColumnGridTheGalleryAlwaysHad() {
        assertEquals(GalleryLayout.Two, GalleryLayoutStore.load(prefs()))
    }

    @Test
    fun everyLayoutComesBackAsSaved() {
        val store = prefs()
        for (layout in GalleryLayout.entries) {
            GalleryLayoutStore.save(store, layout)
            assertEquals(layout, GalleryLayoutStore.load(store))
        }
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheDefault() {
        assertEquals(GalleryLayout.Two, GalleryLayoutStore.load(prefs(mapOf("layout" to "mosaic"))))
    }

    @Test
    fun sliderRoundsToNearestColumnAndClampsAtBothEnds() {
        val cases = listOf(-10f to 1, 1f to 1, 1.49f to 1, 1.5f to 2, 3.5f to 4, 5f to 5, 9f to 5)
        for ((value, expected) in cases) assertEquals(expected, GallerySizing.gridColumns(value))
    }

    @Test
    fun oneAndListForceOneColumnWithoutChangingGridPreference() {
        for (grid in 1..5) {
            assertEquals(1, GallerySizing.columns(GalleryLayout.One, grid))
            assertEquals(1, GallerySizing.columns(GalleryLayout.List, grid))
            assertEquals(grid, GallerySizing.columns(GalleryLayout.Two, grid))
            assertEquals(grid, GallerySizing.columns(GalleryLayout.Large, grid))
        }
        assertEquals(1, GallerySizing.columns(GalleryLayout.Two, -1))
        assertEquals(5, GallerySizing.columns(GalleryLayout.Large, 12))
    }

    @Test
    fun gridColumnsPersistIndependentlyOfOneAndListLayouts() {
        val store = prefs()
        assertEquals(2, GalleryLayoutStore.loadGridColumns(store))
        GalleryLayoutStore.saveGridColumns(store, 4)
        for (layout in listOf(GalleryLayout.One, GalleryLayout.List, GalleryLayout.Two)) {
            GalleryLayoutStore.save(store, layout)
            assertEquals(4, GalleryLayoutStore.loadGridColumns(store))
        }
        GalleryLayoutStore.saveGridColumns(store, 99)
        assertEquals(5, GalleryLayoutStore.loadGridColumns(store))
        assertEquals(1, GalleryLayoutStore.loadGridColumns(prefs(mapOf("grid_columns" to -9))))
        assertEquals(5, GalleryLayoutStore.loadGridColumns(prefs(mapOf("grid_columns" to 9))))
    }
}
