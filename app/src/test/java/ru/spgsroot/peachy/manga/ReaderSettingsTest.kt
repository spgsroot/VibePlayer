package ru.spgsroot.peachy.manga

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.spgsroot.peachy.translate.TranslateEngine

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

    @Test
    fun translateModeDefaultsToManual() {
        assertEquals(TranslateMode.Manual, ReaderSettingsStore.load(prefs()).translateMode)
    }

    @Test
    fun everyTranslateModeComesBackUnchanged() {
        for (mode in TranslateMode.entries) {
            val store = prefs()
            ReaderSettingsStore.save(store, ReaderSettings(orientation = ReaderOrientation.Horizontal, translateMode = mode))
            assertEquals(mode, ReaderSettingsStore.load(store).translateMode)
        }
    }

    @Test
    fun storedTranslateModeValuesAreStable() {
        assertEquals(TranslateMode.OnTurn, ReaderSettingsStore.load(prefs(mapOf("translate_mode" to "on_turn"))).translateMode)
        assertEquals(TranslateMode.Whole, ReaderSettingsStore.load(prefs(mapOf("translate_mode" to "whole"))).translateMode)
    }

    @Test
    fun unknownTranslateModeFallsBackToManual() {
        assertEquals(TranslateMode.Manual, ReaderSettingsStore.load(prefs(mapOf("translate_mode" to "always"))).translateMode)
    }

    @Test
    fun translationSettingsDefaultToModelAutoAppAndNoOverlay() {
        for (settings in listOf(
            ReaderSettings(ReaderOrientation.Horizontal),
            ReaderSettingsStore.load(prefs())
        )) {
            assertEquals(TranslateEngine.Model, settings.engine)
            assertEquals("auto", settings.sourceLanguage)
            assertEquals("app", settings.targetLanguage)
            assertFalse(settings.overlay)
        }
    }

    @Test
    fun allTranslationFieldsRoundTripWithDefaultsAndExplicitChoices() {
        val choices = listOf(
            ReaderSettings(ReaderOrientation.Horizontal),
            ReaderSettings(
                orientation = ReaderOrientation.Vertical, translateMode = TranslateMode.Whole,
                engine = TranslateEngine.MlKit, sourceLanguage = "ja", targetLanguage = "de", overlay = true
            ),
            ReaderSettings(
                orientation = ReaderOrientation.Horizontal,
                engine = TranslateEngine.Model, sourceLanguage = "ko", targetLanguage = "xx", overlay = false
            )
        )
        for (chosen in choices) {
            val store = prefs()
            ReaderSettingsStore.save(store, chosen)
            assertEquals(chosen, ReaderSettingsStore.load(store))
        }
    }

    @Test
    fun storedTranslationFieldsUseStablePreferenceKeysAndWires() {
        val settings = ReaderSettingsStore.load(
            prefs(mapOf(
                "translate_engine" to "mlkit",
                "source_language" to "zh",
                "target_language" to "uk",
                "overlay" to true
            ))
        )
        assertEquals(TranslateEngine.MlKit, settings.engine)
        assertEquals("zh", settings.sourceLanguage)
        assertEquals("uk", settings.targetLanguage)
        assertTrue(settings.overlay)
        val store = prefs()
        ReaderSettingsStore.save(store, settings)
        assertEquals("mlkit", store.getString("translate_engine", null))
        assertEquals("zh", store.getString("source_language", null))
        assertEquals("uk", store.getString("target_language", null))
        assertTrue(store.getBoolean("overlay", false))
    }

    @Test
    fun unsupportedStoredEngineFallsBackToModelWithoutResettingOtherChoices() {
        val settings = ReaderSettingsStore.load(
            prefs(mapOf(
                "translate_engine" to "future",
                "source_language" to "ja",
                "target_language" to "fr",
                "overlay" to true
            ))
        )
        assertEquals(TranslateEngine.Model, settings.engine)
        assertEquals("ja", settings.sourceLanguage)
        assertEquals("fr", settings.targetLanguage)
        assertTrue(settings.overlay)
    }

    @Test
    fun engineFromWireAcceptsKnownValuesAndFallsBackForUnknownOrNull() {
        assertEquals(TranslateEngine.Model, TranslateEngine.fromWire("model"))
        assertEquals(TranslateEngine.MlKit, TranslateEngine.fromWire("mlkit"))
        assertEquals(TranslateEngine.Model, TranslateEngine.fromWire("future"))
        assertEquals(TranslateEngine.Model, TranslateEngine.fromWire(""))
        assertEquals(TranslateEngine.Model, TranslateEngine.fromWire(null))
    }
}
