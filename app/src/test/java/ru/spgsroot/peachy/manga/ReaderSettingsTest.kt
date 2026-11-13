package ru.spgsroot.peachy.manga

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSettingsTest {
    /** A map-backed [SharedPreferences]; only the calls the store makes are supported. */
    private fun prefs(initial: Map<String, Any> = emptyMap()): SharedPreferences {
        val values = initial.toMutableMap()
        lateinit var editor: Any
        editor = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method.name) {
                "putString", "putBoolean" -> { values[args[0] as String] = args[1]!!; editor }
                "apply", "commit" -> true
                else -> error("unexpected ${method.name}")
            }
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0] as String] ?: args[1]
                "getBoolean" -> values[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> error("unexpected ${method.name}")
            }
        } as SharedPreferences
    }

    @Test
    fun freshInstallReadsHorizontalPagedWithTapsOn() {
        val s = ReaderSettingsStore.load(prefs())
        assertEquals(ReaderOrientation.Horizontal, s.orientation)
        assertFalse(s.continuous)
        assertTrue(s.tapToSwitch)
    }

    @Test
    fun savedSettingsComeBackUnchanged() {
        val store = prefs()
        val chosen = ReaderSettings(
            orientation = ReaderOrientation.Vertical, fit = ReaderFit.Width, tapToSwitch = false,
            handSwap = true, inspect = true, continuous = true, hideHints = true
        )
        ReaderSettingsStore.save(store, chosen)
        assertEquals(chosen, ReaderSettingsStore.load(store))
    }

    @Test
    fun olderStripChoiceBecomesVerticalContinuous() {
        val s = ReaderSettingsStore.load(prefs(mapOf("mode" to "strip")))
        assertEquals(ReaderOrientation.Vertical, s.orientation)
        assertTrue(s.continuous)
    }

    @Test
    fun newerChoiceWinsOverTheOlderOne() {
        val s = ReaderSettingsStore.load(
            prefs(mapOf("mode" to "strip", "orientation" to "horizontal", "continuous" to false))
        )
        assertEquals(ReaderOrientation.Horizontal, s.orientation)
        assertFalse(s.continuous)
    }
}
