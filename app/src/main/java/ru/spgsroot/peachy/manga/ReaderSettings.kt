package ru.spgsroot.peachy.manga

import android.content.SharedPreferences
import ru.spgsroot.peachy.translate.TranslateEngine

enum class ReaderOrientation { Vertical, Horizontal }

enum class ReaderFit { Contain, Width }

/** When the reader asks for a translation: only on request, on every settled page, or for the whole gallery. */
enum class TranslateMode(val wire: String) {
    Manual("manual"), OnTurn("on_turn"), Whole("whole");

    companion object {
        fun fromWire(value: String?): TranslateMode = entries.firstOrNull { it.wire == value } ?: Manual
    }
}

/** How the native reader behaves; one set for every gallery. */
data class ReaderSettings(
    val orientation: ReaderOrientation,
    val fit: ReaderFit = ReaderFit.Contain,
    val tapToSwitch: Boolean = true,
    /** Swaps which side goes back and which goes forward. */
    val handSwap: Boolean = false,
    /** Shows zoom buttons; pinch and double tap work either way. */
    val inspect: Boolean = false,
    /** One endless strip instead of one page at a time. */
    val continuous: Boolean = false,
    val hideHints: Boolean = false,
    val translateMode: TranslateMode = TranslateMode.Manual,
    val engine: TranslateEngine = TranslateEngine.Model,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "app",
    val overlay: Boolean = false
)

object ReaderSettingsStore {
    private const val ORIENTATION = "orientation"
    private const val FIT = "fit"
    private const val TAP = "tap"
    private const val HAND_SWAP = "mirror"
    private const val INSPECT = "inspect"
    private const val CONTINUOUS = "continuous"
    private const val HIDE_HINTS = "hide_hints"
    private const val TRANSLATE_MODE = "translate_mode"
    private const val TRANSLATE_ENGINE = "translate_engine"
    private const val SOURCE_LANGUAGE = "source_language"
    private const val TARGET_LANGUAGE = "target_language"
    private const val OVERLAY = "overlay"
    private const val LEGACY_MODE = "mode"

    /** Reads the settings, carrying over the older "paged"/"strip" choice when nothing newer was stored. */
    fun load(prefs: SharedPreferences): ReaderSettings {
        val legacyStrip = prefs.getString(LEGACY_MODE, "paged") == "strip"
        val orientation = when (prefs.getString(ORIENTATION, null)) {
            "vertical" -> ReaderOrientation.Vertical
            "horizontal" -> ReaderOrientation.Horizontal
            else -> if (legacyStrip) ReaderOrientation.Vertical else ReaderOrientation.Horizontal
        }
        return ReaderSettings(
            orientation = orientation,
            fit = if (prefs.getString(FIT, "contain") == "width") ReaderFit.Width else ReaderFit.Contain,
            tapToSwitch = prefs.getBoolean(TAP, true),
            handSwap = prefs.getBoolean(HAND_SWAP, false),
            inspect = prefs.getBoolean(INSPECT, false),
            continuous = prefs.getBoolean(CONTINUOUS, legacyStrip),
            hideHints = prefs.getBoolean(HIDE_HINTS, false),
            translateMode = TranslateMode.fromWire(prefs.getString(TRANSLATE_MODE, null)),
            engine = TranslateEngine.fromWire(prefs.getString(TRANSLATE_ENGINE, null)),
            sourceLanguage = prefs.getString(SOURCE_LANGUAGE, "auto") ?: "auto",
            targetLanguage = prefs.getString(TARGET_LANGUAGE, "app") ?: "app",
            overlay = prefs.getBoolean(OVERLAY, false)
        )
    }

    fun save(prefs: SharedPreferences, settings: ReaderSettings) {
        prefs.edit()
            .putString(ORIENTATION, if (settings.orientation == ReaderOrientation.Vertical) "vertical" else "horizontal")
            .putString(FIT, if (settings.fit == ReaderFit.Width) "width" else "contain")
            .putBoolean(TAP, settings.tapToSwitch)
            .putBoolean(HAND_SWAP, settings.handSwap)
            .putBoolean(INSPECT, settings.inspect)
            .putBoolean(CONTINUOUS, settings.continuous)
            .putBoolean(HIDE_HINTS, settings.hideHints)
            .putString(TRANSLATE_MODE, settings.translateMode.wire)
            .putString(TRANSLATE_ENGINE, settings.engine.wire)
            .putString(SOURCE_LANGUAGE, settings.sourceLanguage)
            .putString(TARGET_LANGUAGE, settings.targetLanguage)
            .putBoolean(OVERLAY, settings.overlay)
            .apply()
    }
}
