package ru.spgsroot.peachy.translate

/**
 * A provider the dialog can fill in with one tap: its OpenAI-compatible address and a cheap model that reads images.
 * The key is never part of a preset; it is the user's own. Model names change often, so the field stays editable.
 */
data class ProviderPreset(val id: String, val name: String, val baseUrl: String, val model: String)

object ProviderPresets {
    val all: List<ProviderPreset> = listOf(
        ProviderPreset("opencode-go", "OpenCode Go", DEFAULT_BASE_URL, DEFAULT_MODEL),
        ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-6-luna"),
        ProviderPreset(
            "gemini",
            "Google Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "gemini-3.5-flash-lite"
        ),
        ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "z-ai/glm-5.3-flash"),
        ProviderPreset("mistral", "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest")
    )

    /** The preset that owns [baseUrl], ignoring case, spaces and a trailing slash; null for any other address. */
    fun find(baseUrl: String): ProviderPreset? {
        val wanted = ProviderUrl.normalized(baseUrl)
        return all.firstOrNull { ProviderUrl.normalized(it.baseUrl).equals(wanted, ignoreCase = true) }
    }
}
