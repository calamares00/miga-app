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

const val DEFAULT_MISTRAL_MODEL = "mistral-medium-latest"

const val DEFAULT_GROQ_MODEL = "qwen/qwen3.8-27b"

/** Model used before the user picks one; OpenRouter and a custom server have no default. */
fun defaultModelFor(provider: AiProvider): String = when (provider) {
    AiProvider.GEMINI -> DEFAULT_GEMINI_MODEL
    AiProvider.ANTHROPIC -> DEFAULT_ANTHROPIC_MODEL
    AiProvider.OPENAI -> DEFAULT_OPENAI_MODEL
    AiProvider.DEEPSEEK -> DEFAULT_DEEPSEEK_MODEL
    AiProvider.MISTRAL -> DEFAULT_MISTRAL_MODEL
    AiProvider.GROQ -> DEFAULT_GROQ_MODEL
    else -> ""
}

/**
 * First guess of whether a model reads images, from its name. Every Gemini and Claude model does;
 * an OpenRouter model is assumed to until the catalogue says otherwise. Settings shows the guess
 * as a switch the user can correct where it is uncertain.
 */
fun guessModelReadsImages(provider: AiProvider, model: String): Boolean {
    val id = model.lowercase()
    val markers = when (provider) {
        AiProvider.GEMINI, AiProvider.ANTHROPIC, AiProvider.OPENROUTER -> return true
        AiProvider.OPENAI -> return openAiModelReadsImages(model)
        AiProvider.DEEPSEEK -> return deepSeekModelReadsImages(model)
        AiProvider.MISTRAL -> listOf("pixtral", "medium", "large", "ministral")
        AiProvider.GROQ -> listOf("qwen3.8", "llama-4", "scout", "maverick", "vision", "muse-spark")
        AiProvider.CUSTOM -> listOf(
            "vision", "-vl", "vl-", "llava", "pixtral", "gemma3", "gemma-3", "llama-4", "llama4", "minicpm-v",
            "moondream", "gpt-4o", "gpt-5", "claude", "gemini", "deepseek-flash", "qwen3.8"
        )
    }
    return markers.any { it in id }
}

const val DEFAULT_GEMINI_MODEL = "gemini-3.6-flash"

const val DEFAULT_ANTHROPIC_MODEL = "claude-haiku-4-5"

const val DEFAULT_OPENAI_MODEL = "gpt-5-mini"

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

/** Whether Settings shows the "reads images" switch: not for providers whose models all read images. */
fun AiProvider.imageSupportVaries(): Boolean = this != AiProvider.GEMINI && this != AiProvider.ANTHROPIC && this != AiProvider.OPENROUTER
