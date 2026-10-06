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

private const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val TIMEOUT_MILLIS = 60_000

@Serializable
internal data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>
)

/** Fields with a default value are only encoded when marked with @EncodeDefault. */
@Serializable
internal data class AnthropicMessage(@EncodeDefault val role: String = "user", val content: List<AnthropicContentBlock>)

@Serializable
internal data class AnthropicContentBlock(
    val type: String,
    val text: String? = null,
    val source: AnthropicImageSource? = null
)

@Serializable
internal data class AnthropicImageSource(
    @EncodeDefault val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String
)

@Serializable
internal data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
internal data class AnthropicErrorEnvelope(val error: AnthropicErrorDetail? = null)

@Serializable
internal data class AnthropicErrorDetail(val message: String? = null)

/** Anthropic Messages API (Claude). */
internal object AnthropicTransport : AiTransport {

    override suspend fun complete(request: AiRequest, apiKey: String, model: String): AiText = withContext(Dispatchers.IO) {
        try {
            val content = request.images.map {
                AnthropicContentBlock(
                    type = "image",
                    source = AnthropicImageSource(mediaType = it.mimeType, data = Base64.getEncoder().encodeToString(it.bytes))
                )
            } + AnthropicContentBlock(type = "text", text = request.prompt)
            val body = aiJson.encodeToString(
                AnthropicRequest.serializer(),
                AnthropicRequest(model = model, maxTokens = request.maxTokens, messages = listOf(AnthropicMessage(content = content)))
            )
            val response = postJson(
                url = ANTHROPIC_ENDPOINT,
                body = body,
                headers = mapOf("x-api-key" to apiKey, "anthropic-version" to ANTHROPIC_VERSION),
                timeoutMillis = TIMEOUT_MILLIS
            )
            if (!response.isSuccessful) {
                val message = runCatching { aiJson.decodeFromString(AnthropicErrorEnvelope.serializer(), response.body).error?.message }.getOrNull()
                return@withContext AiText.Error(AiErrors.http(AiProvider.ANTHROPIC.shortName, response.code, message))
            }
            val decoded = aiJson.decodeFromString(AnthropicResponse.serializer(), response.body)
            val text = decoded.content.firstOrNull { it.type == "text" }?.text
            if (text.isNullOrBlank()) AiText.Error(describeIncomplete(decoded.stopReason)) else AiText.Success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiText.Error(AiErrors.exception(e))
        }
    }

    /** Explains why Claude returned no text (output limit reached, refusal...). */
    private fun describeIncomplete(stopReason: String?): String {
        val name = AiProvider.ANTHROPIC.shortName
        return ErrorDetail.markAsAi(
            when (stopReason) {
                "max_tokens" -> L10n.str(R.string.ai_incomplete_too_long, name)
                "refusal" -> L10n.str(R.string.ai_incomplete_refused, name)
                null -> L10n.str(R.string.ai_incomplete_empty, name)
                else -> L10n.str(R.string.ai_incomplete_other, name, stopReason)
            }
        )
    }
}
