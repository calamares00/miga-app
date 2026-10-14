@file:OptIn(ExperimentalSerializationApi::class)

package org.calamares.miga.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.data.support.ErrorDetail
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

private const val OPENROUTER_CHAT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
private const val OPENROUTER_MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models"

// Free OpenRouter models are usually slower than the providers' own APIs.
private const val TIMEOUT_MILLIS = 60_000

// Output budget for the second attempt after an answer was cut off.
private const val LARGE_MAX_TOKENS = 32_000

// Request and response DTOs follow the OpenAI chat completions format used by OpenRouter.

@Serializable
internal data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    /** Omitted when null; see [OpenRouterTransport.complete] for why a request may be retried without it. */
    @SerialName("max_tokens") val maxTokens: Int? = null
)

/** Fields with a default value are only encoded when marked with @EncodeDefault. */
@Serializable
internal data class OpenRouterMessage(@EncodeDefault val role: String = "user", val content: List<OpenRouterPart>)

@Serializable
internal data class OpenRouterPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: OpenRouterImageUrl? = null
)

@Serializable
internal data class OpenRouterImageUrl(val url: String)

@Serializable
internal data class OpenRouterResponse(
    val choices: List<OpenRouterChoice> = emptyList(),
    val error: OpenRouterError? = null
)

@Serializable
internal data class OpenRouterChoice(
    val message: OpenRouterResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
internal data class OpenRouterResponseMessage(val content: String? = null)

@Serializable
internal data class OpenRouterError(
    val message: String? = null,
    val code: Int? = null,
    val metadata: OpenRouterErrorMetadata? = null
)

/**
 * When the upstream provider fails, OpenRouter only says "Provider returned error" and puts the
 * real reason in `metadata.raw` (text or JSON), together with the provider name.
 */
@Serializable
internal data class OpenRouterErrorMetadata(
    val raw: JsonElement? = null,
    @SerialName("provider_name") val providerName: String? = null
)

@Serializable
internal data class OpenRouterErrorEnvelope(val error: OpenRouterError? = null)

/** Error message including the upstream provider's real reason, when present. */
internal fun OpenRouterError.describe(): String? {
    val raw = metadata?.raw?.let { element ->
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: element.toString()
    }?.take(1500)
    return listOfNotNull(message, metadata?.providerName?.let { "Provider: $it" }, raw)
        .joinToString("\n")
        .ifBlank { null }
}

/** OpenRouter chat completions API. */
internal object OpenRouterTransport : AiTransport {

    private val name = AiProvider.OPENROUTER.shortName

    override suspend fun complete(request: AiRequest, apiKey: String, model: String): AiText = withContext(Dispatchers.IO) {
        try {
            val parts = request.images.map {
                OpenRouterPart(
                    type = "image_url",
                    imageUrl = OpenRouterImageUrl("data:${it.mimeType};base64," + Base64.getEncoder().encodeToString(it.bytes))
                )
            } + OpenRouterPart(type = "text", text = request.prompt)
            val messages = listOf(OpenRouterMessage(content = parts))
            var answer = send(model, messages, request.maxTokens, apiKey)
            // Reasoning models spend part of the budget thinking and may run out before finishing
            // the answer, which leaves a cut JSON. Try once more with a larger budget.
            if (answer is Answer.Truncated && request.maxTokens < LARGE_MAX_TOKENS) {
                answer = send(model, messages, LARGE_MAX_TOKENS, apiKey)
            }
            when (val result = answer) {
                is Answer.Complete -> AiText.Success(result.text)
                is Answer.Truncated -> AiText.Error(
                    ErrorDetail.withDetail(describeIncomplete("length"), result.partialText.take(1500))
                )
                is Answer.Failed -> AiText.Error(result.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiText.Error(AiErrors.exception(e))
        }
    }

    private sealed interface Answer {
        class Complete(val text: String) : Answer
        class Truncated(val partialText: String) : Answer
        class Failed(val reason: String) : Answer
    }

    /** One completion request, retried without max_tokens when the upstream provider rejects it. */
    private suspend fun send(model: String, messages: List<OpenRouterMessage>, maxTokens: Int, apiKey: String): Answer {
        var response = post(OpenRouterRequest(model, messages, maxTokens), apiKey)
        var errorDetail: String? = null
        // A 400 usually means the upstream provider rejects a parameter; with free models it is
        // most often a max_tokens above their output limit. Retry once letting the provider pick.
        if (response.code == 400) {
            val retry = post(OpenRouterRequest(model, messages), apiKey)
            if (retry.isSuccessful) {
                response = retry
            } else {
                errorDetail = describeErrorBody(response.body) + "\n— retry without max_tokens:\n" + describeErrorBody(retry.body)
            }
        }
        if (!response.isSuccessful) {
            return Answer.Failed(AiErrors.http(name, response.code, errorDetail ?: describeErrorBody(response.body)))
        }
        val decoded = aiJson.decodeFromString(OpenRouterResponse.serializer(), response.body)
        // OpenRouter may answer 200 with the upstream provider's error inside the body.
        decoded.error?.let { error -> return Answer.Failed(AiErrors.http(name, error.code ?: 502, error.describe())) }
        val choice = decoded.choices.firstOrNull()
        val text = choice?.message?.content
        val truncated = choice?.finishReason == "length"
        if (text.isNullOrBlank()) {
            return if (truncated) Answer.Truncated("") else Answer.Failed(describeIncomplete(choice?.finishReason))
        }
        return if (truncated) Answer.Truncated(text) else Answer.Complete(text)
    }

    private suspend fun post(request: OpenRouterRequest, apiKey: String): HttpResponse = postJson(
        url = OPENROUTER_CHAT_ENDPOINT,
        body = aiJson.encodeToString(OpenRouterRequest.serializer(), request),
        headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            // Optional attribution headers requested by OpenRouter.
            "HTTP-Referer" to "https://miga.calamares.org",
            "X-Title" to "Miga"
        ),
        timeoutMillis = TIMEOUT_MILLIS
    )

    private fun describeErrorBody(body: String): String =
        runCatching { aiJson.decodeFromString(OpenRouterErrorEnvelope.serializer(), body).error?.describe() }.getOrNull()
            ?: body.take(1500)

    private fun describeIncomplete(finishReason: String?): String = ErrorDetail.markAsAi(
        when (finishReason) {
            "length" -> L10n.str(R.string.ai_incomplete_too_long, name)
            "content_filter" -> L10n.str(R.string.ai_incomplete_refused, name)
            null -> L10n.str(R.string.ai_incomplete_empty, name)
            else -> L10n.str(R.string.ai_incomplete_other, name, finishReason)
        }
    )
}

@Serializable
internal data class OpenRouterModelsResponse(val data: List<OpenRouterModelDto> = emptyList())

@Serializable
internal data class OpenRouterModelDto(
    val id: String,
    val name: String = "",
    val pricing: OpenRouterPricing? = null,
    val architecture: OpenRouterArchitecture? = null
)

@Serializable
internal data class OpenRouterPricing(val prompt: String? = null, val completion: String? = null)

@Serializable
internal data class OpenRouterArchitecture(
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList()
)

/** An OpenRouter model as shown in Settings. */
data class OpenRouterModel(val id: String, val name: String, val isFree: Boolean, val supportsImages: Boolean)

/**
 * OpenRouter's public model catalogue (no key needed). Downloaded when the picker opens and kept
 * in memory while the process lives.
 */
object OpenRouterModels {

    private val mutex = Mutex()
    private var cache: List<OpenRouterModel>? = null

    /** Text-output models with free ones first, or the last cached list (or null) when offline. */
    suspend fun fetch(forceRefresh: Boolean = false): List<OpenRouterModel>? = mutex.withLock {
        if (!forceRefresh) cache?.let { return@withLock it }
        val fetched = withContext(Dispatchers.IO) { download() }
        if (fetched != null) cache = fetched
        fetched ?: cache
    }

    /** Last downloaded list, without touching the network (used to label an already chosen model). */
    fun cached(): List<OpenRouterModel>? = cache

    private fun download(): List<OpenRouterModel>? = try {
        val connection = URL(OPENROUTER_MODELS_ENDPOINT).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                aiJson.decodeFromString(OpenRouterModelsResponse.serializer(), body).data
                    .filter { dto -> dto.architecture?.outputModalities.let { it.isNullOrEmpty() || "text" in it } }
                    .map { dto ->
                        OpenRouterModel(
                            id = dto.id,
                            name = dto.name.ifBlank { dto.id },
                            isFree = isFreeModel(dto.id, dto.pricing?.prompt, dto.pricing?.completion),
                            supportsImages = "image" in dto.architecture?.inputModalities.orEmpty()
                        )
                    }
                    .sortedWith(compareByDescending<OpenRouterModel> { it.isFree }.thenBy { it.name.lowercase() })
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

/** A model is free when its id has the ":free" suffix or both input and output tokens cost 0. */
internal fun isFreeModel(id: String, promptPrice: String?, completionPrice: String?): Boolean =
    id.endsWith(":free") ||
        (promptPrice?.toDoubleOrNull() == 0.0 && completionPrice?.toDoubleOrNull() == 0.0)
