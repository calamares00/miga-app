package org.calamares.miga.data.ai

/**
 * AI providers the user can configure. Constant names are persisted in the settings (provider
 * order), so they must not be renamed.
 */
enum class AiProvider(val label: String, val shortName: String) {
    GEMINI("Google Gemini", "Gemini"),
    ANTHROPIC("Anthropic Claude", "Claude"),
    OPENROUTER("OpenRouter", "OpenRouter"),
    OPENAI("OpenAI", "OpenAI")
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

/**
 * OpenAI models offered in Settings until the list of the user's account is downloaded (see
 * [ProviderModels]); any other id can also be typed in by hand.
 */
val OPENAI_MODELS = listOf(
    "gpt-5-mini",
    "gpt-5",
    "gpt-4.1-mini",
    "gpt-4o-mini"
)

const val DEFAULT_OPENAI_MODEL = "gpt-5-mini"

/** Whether an OpenAI model reads images: all current chat models do, except a few small reasoning ones. */
fun openAiModelReadsImages(model: String): Boolean {
    val id = model.lowercase()
    return listOf("o1-mini", "o3-mini", "gpt-3.5").none { id.startsWith(it) }
}
