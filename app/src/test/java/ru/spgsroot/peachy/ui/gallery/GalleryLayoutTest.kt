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
                "putString" -> { values[args[0] as String] = args[1]!!; editor }
                "apply", "commit" -> true
                else -> error("unexpected ${method.name}")
            }
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0] as String] ?: args[1]
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
}
