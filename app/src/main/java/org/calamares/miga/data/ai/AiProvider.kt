package org.calamares.miga.data.ai

/**
 * AI providers the user can configure. Constant names are persisted in the settings (provider
 * order), so they must not be renamed.
 */
enum class AiProvider(val label: String, val shortName: String) {
    GEMINI("Google Gemini", "Gemini"),
    ANTHROPIC("Anthropic Claude", "Claude"),
    OPENROUTER("OpenRouter", "OpenRouter")
}

/**
 * Gemini models offered in Settings. Google retires model ids over time, so Settings also lets the
 * user type any other id; see https://ai.google.dev/gemini-api/docs/models for the current list.
 */
val GEMINI_MODELS = listOf(
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite"
)

const val DEFAULT_GEMINI_MODEL = "gemini-3.6-flash"

/** Claude models offered in Settings; any other id can also be typed in by hand. */
val ANTHROPIC_MODELS = listOf(
    "claude-opus-5",
    "claude-sonnet-5",
    "claude-haiku-4-5"
)

const val DEFAULT_ANTHROPIC_MODEL = "claude-haiku-4-5"
