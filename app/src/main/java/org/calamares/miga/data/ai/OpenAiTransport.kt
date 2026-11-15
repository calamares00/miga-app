@file:OptIn(ExperimentalSerializationApi::class)

package org.calamares.miga.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.data.support.AppDispatchers
import org.calamares.miga.data.support.ErrorDetail
import java.util.Base64
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

private const val OPENAI_CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
private const val DEEPSEEK_CHAT_ENDPOINT = "https://api.deepseek.com/chat/completions"
private const val MISTRAL_CHAT_ENDPOINT = "https://api.mistral.ai/v1/chat/completions"
private const val GROQ_CHAT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

// Models on a home server (Ollama, LM Studio) can be much slower than the hosted APIs.
private const val CUSTOM_TIMEOUT_MILLIS = 300_000
private const val TIMEOUT_MILLIS = 90_000

// Output budget for the second attempt after an answer was cut off.
private const val LARGE_MAX_TOKENS = 32_000

/**
 * Chat completions request. OpenAI takes max_completion_tokens, not max_tokens: its reasoning
 * models (o-series, GPT-5) reject the old parameter, and it also bounds their hidden reasoning.
 * Other APIs with the same format (DeepSeek) take max_tokens. Only the one that is set is sent.
 */
@Serializable
internal data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

/** Fields with a default value are only encoded when marked with @EncodeDefault. */
@Serializable
internal data class OpenAiMessage(@EncodeDefault val role: String = "user", val content: List<OpenAiPart>)

@Serializable
internal data class OpenAiPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: OpenAiImageUrl? = null
)

@Serializable
internal data class OpenAiImageUrl(val url: String)

@Serializable
internal data class OpenAiResponse(val choices: List<OpenAiChoice> = emptyList())

@Serializable
internal data class OpenAiChoice(
    val message: OpenAiResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
internal data class OpenAiResponseMessage(val content: String? = null, val refusal: String? = null)

@Serializable
internal data class OpenAiErrorEnvelope(val error: OpenAiErrorDetail? = null)

@Serializable
internal data class OpenAiErrorDetail(val message: String? = null, val code: String? = null)

/** OpenAI chat completions API (GPT models). */
internal val OpenAiTransport: AiTransport = OpenAiCompatibleTransport(AiProvider.OPENAI, OPENAI_CHAT_ENDPOINT, useMaxCompletionTokens = true)

/** DeepSeek's API, which follows the OpenAI chat completions format, images included. */
internal val DeepSeekTransport: AiTransport = OpenAiCompatibleTransport(AiProvider.DEEPSEEK, DEEPSEEK_CHAT_ENDPOINT, useMaxCompletionTokens = false)

/** Mistral's API, in the OpenAI chat completions format. */
internal val MistralTransport: AiTransport = OpenAiCompatibleTransport(AiProvider.MISTRAL, MISTRAL_CHAT_ENDPOINT, useMaxCompletionTokens = false)

/** Groq's OpenAI-compatible API. */
internal val GroqTransport: AiTransport = OpenAiCompatibleTransport(AiProvider.GROQ, GROQ_CHAT_ENDPOINT, useMaxCompletionTokens = true)

/** A server with OpenAI's chat completions API at the address the user entered. */
internal fun customTransport(baseUrl: String): AiTransport =
    OpenAiCompatibleTransport(AiProvider.CUSTOM, customChatEndpoint(baseUrl), useMaxCompletionTokens = false, timeoutMillis = CUSTOM_TIMEOUT_MILLIS)

/**
 * Chat completions address for what the user typed: a base such as "http://192.168.1.10:11434/v1"
 * or "https://opencode.ai/zen/v1", or the full address already ending in /chat/completions.
 */
internal fun customChatEndpoint(baseUrl: String): String {
    val base = baseUrl.trim().trimEnd('/')
    return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
}

/** Models list address of the same server. */
internal fun customModelsEndpoint(baseUrl: String): String =
    baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions").trimEnd('/') + "/models"

/**
 * A chat completions API in OpenAI's format at [endpoint], with a Bearer key (left out when the
 * key is blank, as a home server needs none).
 */
internal class OpenAiCompatibleTransport(
    provider: AiProvider,
    private val endpoint: String,
    private val useMaxCompletionTokens: Boolean,
    private val timeoutMillis: Int = TIMEOUT_MILLIS
) : AiTransport {

    private val name = provider.shortName

    override suspend fun complete(request: AiRequest, apiKey: String, model: String): AiText = withContext(AppDispatchers.io) {
        try {
            val parts = request.images.map {
                OpenAiPart(
                    type = "image_url",
                    imageUrl = OpenAiImageUrl("data:${it.mimeType};base64," + Base64.getEncoder().encodeToString(it.bytes))
                )
            } + OpenAiPart(type = "text", text = request.prompt)
            val messages = listOf(OpenAiMessage(content = parts))
            var answer = send(model, messages, request.maxTokens, apiKey)
            // Reasoning models spend part of the budget thinking and may run out before writing
            // the answer, which leaves it empty or cut. Try once more with a larger budget.
            if (answer is Answer.Truncated && request.maxTokens < LARGE_MAX_TOKENS) {
                answer = send(model, messages, LARGE_MAX_TOKENS, apiKey)
            }
            when (val result = answer) {
                is Answer.Complete -> AiText.Success(result.text)
                is Answer.Truncated -> AiText.Error(ErrorDetail.withDetail(describeIncomplete("length"), result.partialText.take(1500)))
                is Answer.Failed -> AiText.Error(result.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            AiText.Error(AiErrors.exception(e))
        }
    }

    private sealed interface Answer {
        class Complete(val text: String) : Answer
        class Truncated(val partialText: String) : Answer
        class Failed(val reason: String) : Answer
    }

    private suspend fun send(model: String, messages: List<OpenAiMessage>, maxTokens: Int, apiKey: String): Answer {
        val response = postJson(
            url = endpoint,
            body = aiJson.encodeToString(
                OpenAiRequest.serializer(),
                if (useMaxCompletionTokens) OpenAiRequest(model, messages, maxCompletionTokens = maxTokens)
                else OpenAiRequest(model, messages, maxTokens = maxTokens)
            ),
            headers = if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey"),
            timeoutMillis = timeoutMillis
        )
        if (!response.isSuccessful) {
            val message = runCatching { aiJson.decodeFromString(OpenAiErrorEnvelope.serializer(), response.body).error?.message }.getOrNull()
            return Answer.Failed(AiErrors.http(name, response.code, message ?: response.body.take(1500)))
        }
        val choice = aiJson.decodeFromString(OpenAiResponse.serializer(), response.body).choices.firstOrNull()
        val text = choice?.message?.content
        choice?.message?.refusal?.takeIf { it.isNotBlank() }?.let {
            return Answer.Failed(ErrorDetail.withDetail(describeIncomplete("content_filter"), it))
        }
        val truncated = choice?.finishReason == "length"
        if (text.isNullOrBlank()) {
            return if (truncated) Answer.Truncated("") else Answer.Failed(describeIncomplete(choice?.finishReason))
        }
        return if (truncated) Answer.Truncated(text) else Answer.Complete(text)
    }

    private fun describeIncomplete(finishReason: String?): String = ErrorDetail.markAsAi(
        when (finishReason) {
            "length" -> L10n.str(R.string.ai_incomplete_too_long, name)
            "content_filter" -> L10n.str(R.string.ai_incomplete_refused, name)
            null, "stop" -> L10n.str(R.string.ai_incomplete_empty, name)
            else -> L10n.str(R.string.ai_incomplete_other, name, finishReason)
        }
    )
}
