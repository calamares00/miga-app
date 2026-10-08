@file:OptIn(ExperimentalSerializationApi::class)

package org.calamares.miga.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.data.support.ErrorDetail
import java.util.Base64

private const val OPENAI_CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
private const val TIMEOUT_MILLIS = 90_000

// Output budget for the second attempt after an answer was cut off.
private const val LARGE_MAX_TOKENS = 32_000

/**
 * Chat completions request. max_completion_tokens, not max_tokens: reasoning models (o-series,
 * GPT-5) reject the old parameter, and it also bounds their hidden reasoning.
 */
@Serializable
internal data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int
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
internal object OpenAiTransport : AiTransport {

    private val name = AiProvider.OPENAI.shortName

    override suspend fun complete(request: AiRequest, apiKey: String, model: String): AiText = withContext(Dispatchers.IO) {
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
            AiText.Error(AiErrors.exception(e))
        }
    }

    private sealed interface Answer {
        class Complete(val text: String) : Answer
        class Truncated(val partialText: String) : Answer
        class Failed(val reason: String) : Answer
    }

    private fun send(model: String, messages: List<OpenAiMessage>, maxTokens: Int, apiKey: String): Answer {
        val response = postJson(
            url = OPENAI_CHAT_ENDPOINT,
            body = aiJson.encodeToString(OpenAiRequest.serializer(), OpenAiRequest(model, messages, maxTokens)),
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            timeoutMillis = TIMEOUT_MILLIS
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
