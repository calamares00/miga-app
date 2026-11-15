package org.calamares.miga.data.ai

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * AI providers the user can configure. Constant names are persisted in the settings (provider
 * order), so they must not be renamed.
 */
enum class AiProvider(private val brandLabel: String, private val brandShortName: String) {
    GEMINI("Google Gemini", "Gemini"),
    ANTHROPIC("Anthropic Claude", "Claude"),
    OPENROUTER("OpenRouter", "OpenRouter"),
    OPENAI("OpenAI", "OpenAI"),
    DEEPSEEK("DeepSeek", "DeepSeek"),
    MISTRAL("Mistral AI", "Mistral"),
    GROQ("Groq", "Groq"),

    /** Any server with OpenAI's chat completions API at an address the user enters (Ollama, LM Studio, OpenCode Zen...). */
    CUSTOM("", "");

    val label: String get() = if (this == CUSTOM) L10n.str(R.string.ai_provider_custom) else brandLabel
    val shortName: String get() = if (this == CUSTOM) L10n.str(R.string.ai_provider_custom_short) else brandShortName
}

/**
 * Providers configured through the generic settings (key, model, "reads images" switch and, for
 * [AiProvider.CUSTOM], the server address) rather than settings of their own.
 */
val GENERIC_PROVIDERS = listOf(AiProvider.MISTRAL, AiProvider.GROQ, AiProvider.CUSTOM)

/** Mistral models offered until the account's list is downloaded; see https://docs.mistral.ai. */
val MISTRAL_MODELS = listOf(
    "mistral-medium-latest",
    "mistral-large-latest",
    "mistral-small-latest",
    "ministral-8b-latest"
)

const val DEFAULT_MISTRAL_MODEL = "mistral-medium-latest"

/** Groq models offered until the account's list is downloaded; see https://console.groq.com/docs/models. */
val GROQ_MODELS = listOf(
    "qwen/qwen3.8-27b",
    "llama-3.3-70b-versatile",
    "openai/gpt-oss-120b"
)

const val DEFAULT_GROQ_MODEL = "qwen/qwen3.8-27b"

/** Model used by a generic provider before the user picks one; a custom server has no default. */
fun defaultModelFor(provider: AiProvider): String = when (provider) {
    AiProvider.MISTRAL -> DEFAULT_MISTRAL_MODEL
    AiProvider.GROQ -> DEFAULT_GROQ_MODEL
    else -> ""
}

/**
 * First guess of whether a generic provider's model reads images, from its name. Settings shows it
 * as a switch the user can correct, and the models list of Mistral states it exactly.
 */
fun guessModelReadsImages(provider: AiProvider, model: String): Boolean {
    val id = model.lowercase()
    val markers = when (provider) {
        AiProvider.MISTRAL -> listOf("pixtral", "medium", "large", "ministral")
        AiProvider.GROQ -> listOf("qwen3.8", "llama-4", "scout", "maverick", "vision", "muse-spark")
        else -> listOf(
            "vision", "-vl", "vl-", "llava", "pixtral", "gemma3", "gemma-3", "llama-4", "llama4", "minicpm-v",
            "moondream", "gpt-4o", "gpt-5", "claude", "gemini", "deepseek-flash", "qwen3.8"
        )
    }
    return markers.any { it in id }
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

/**
 * DeepSeek models offered in Settings until the list of the user's account is downloaded; any
 * other id can also be typed in by hand. See https://api-docs.deepseek.com/quick_start/pricing.
 */
val DEEPSEEK_MODELS = listOf(
    "deepseek-flash",
    "deepseek-v4-pro"
)

const val DEFAULT_DEEPSEEK_MODEL = "deepseek-flash"

/**
 * Whether a DeepSeek model reads images: only the Flash models do (the Pro model answers images
 * with an error). Older Flash ids are still accepted and served by the current Flash model.
 */
fun deepSeekModelReadsImages(model: String): Boolean = "flash" in model.lowercase()

/** Whether an OpenAI model reads images: all current chat models do, except a few small reasoning ones. */
fun openAiModelReadsImages(model: String): Boolean {
    val id = model.lowercase()
    return listOf("o1-mini", "o3-mini", "gpt-3.5").none { id.startsWith(it) }
}
