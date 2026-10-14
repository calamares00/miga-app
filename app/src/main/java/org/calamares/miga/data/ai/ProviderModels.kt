package org.calamares.miga.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@Serializable
internal data class OpenAiModelsResponse(val data: List<OpenAiModelDto> = emptyList())

@Serializable
internal data class OpenAiModelDto(val id: String, val created: Long = 0)

@Serializable
internal data class AnthropicModelsResponse(val data: List<AnthropicModelDto> = emptyList())

@Serializable
internal data class AnthropicModelDto(val id: String)

@Serializable
internal data class GeminiModelsResponse(val models: List<GeminiModelDto> = emptyList())

@Serializable
internal data class GeminiModelDto(
    val name: String,
    @SerialName("supportedGenerationMethods") val methods: List<String> = emptyList()
)

/**
 * Models available to the user's API key, downloaded from each provider's API so Settings lists
 * the current ones instead of a list fixed in the app (providers add and retire models all the
 * time). OpenRouter has its own public catalogue, see [OpenRouterModels].
 *
 * Only models that answer the kind of request Miga sends (text chat, with images for recipes from
 * photos) are kept: no embeddings, speech, image generation or realtime models. Kept in memory per
 * provider and key while the process lives.
 */
object ProviderModels {
    private const val TIMEOUT_MILLIS = 20_000
    private val mutex = Mutex()
    private val cache = mutableMapOf<Pair<AiProvider, String>, List<String>>()

    /** Model ids, newest first, or null when the list cannot be downloaded (no network, wrong key). */
    suspend fun fetch(provider: AiProvider, apiKey: String): List<String>? {
        val key = provider to apiKey.trim()
        if (key.second.isEmpty() || provider == AiProvider.OPENROUTER) return null
        mutex.withLock { cache[key]?.let { return it } }
        val fetched = withContext(Dispatchers.IO) {
            runCatching { download(provider, key.second) }.getOrNull()
        }?.takeIf { it.isNotEmpty() } ?: return null
        mutex.withLock { cache[key] = fetched }
        return fetched
    }

    private fun download(provider: AiProvider, apiKey: String): List<String>? = when (provider) {
        AiProvider.OPENAI -> {
            val response = getJson("https://api.openai.com/v1/models", mapOf("Authorization" to "Bearer $apiKey"), TIMEOUT_MILLIS)
            if (!response.isSuccessful) null else aiJson.decodeFromString(OpenAiModelsResponse.serializer(), response.body).data
                .filter { isOpenAiChatModel(it.id) }
                .sortedByDescending { it.created }
                .map { it.id }
        }
        AiProvider.ANTHROPIC -> {
            val response = getJson(
                "https://api.anthropic.com/v1/models?limit=100",
                mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
                TIMEOUT_MILLIS
            )
            // Already sorted by the API, newest first.
            if (!response.isSuccessful) null else aiJson.decodeFromString(AnthropicModelsResponse.serializer(), response.body).data.map { it.id }
        }
        AiProvider.GEMINI -> {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000&key=" + URLEncoder.encode(apiKey, "UTF-8")
            val response = getJson(url, emptyMap(), TIMEOUT_MILLIS)
            if (!response.isSuccessful) null else aiJson.decodeFromString(GeminiModelsResponse.serializer(), response.body).models
                .filter { "generateContent" in it.methods }
                .map { it.name.removePrefix("models/") }
                .filter { isGeminiChatModel(it) }
                .sortedDescending()
        }
        AiProvider.OPENROUTER -> null
    }
}

private val NON_CHAT_MARKERS = listOf(
    "audio", "realtime", "transcribe", "tts", "image", "embedding", "search", "moderation", "instruct",
    // Only available through OpenAI's Responses API, not chat completions.
    "codex", "-pro", "deep-research", "computer-use"
)

/** OpenAI chat models usable with chat completions (GPT and o-series), without special-purpose variants. */
internal fun isOpenAiChatModel(id: String): Boolean {
    val lower = id.lowercase()
    val family = lower.startsWith("gpt-") || lower.startsWith("chatgpt-") || Regex("^o\\d").containsMatchIn(lower)
    return family && NON_CHAT_MARKERS.none { it in lower } && !lower.startsWith("gpt-3.5")
}

/** Gemini text models, without speech, image generation, live or embedding variants. */
internal fun isGeminiChatModel(id: String): Boolean {
    val lower = id.lowercase()
    return lower.startsWith("gemini") &&
        listOf("tts", "image", "live", "native-audio", "embedding", "robotics", "computer-use").none { it in lower }
}
