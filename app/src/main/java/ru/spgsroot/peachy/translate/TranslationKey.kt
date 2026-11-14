package ru.spgsroot.peachy.translate

/**
 * Builds the opaque string stored in the cache and vault's existing language field.
 * Model + "auto" maps to exactly [target], preserving legacy translations. Every other combination
 * maps to `target|engine.wire|source`, with no omitted fields or language-code normalization.
 * Cache, runner and vault consumers must treat the result as an opaque key, not a language code.
 */
object TranslationKey {
    fun of(engine: TranslateEngine, source: String, target: String): String =
        if (engine == TranslateEngine.Model && source == "auto") target else "$target|${engine.wire}|$source"
}
