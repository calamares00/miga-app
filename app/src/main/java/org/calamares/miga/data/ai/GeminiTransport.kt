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

private const val GEMINI_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
private const val TIMEOUT_MILLIS = 60_000

@Serializable
internal data class GeminiRequest(val contents: List<GeminiContent>, val generationConfig: GeminiGenerationConfig)

@Serializable
internal data class GeminiContent(val parts: List<GeminiPart> = emptyList())

@Serializable
internal data class GeminiPart(
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null,
    val text: String? = null
)

@Serializable
internal data class GeminiInlineData(@SerialName("mime_type") val mimeType: String, val data: String)

/** Fields with a default value are only encoded when marked with @EncodeDefault. */
@Serializable
internal data class GeminiGenerationConfig(@EncodeDefault val responseMimeType: String = "application/json")

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
internal data class GeminiCandidate(val content: GeminiContent? = null, val finishReason: String? = null)

@Serializable
internal data class GeminiPromptFeedback(val blockReason: String? = null)

@Serializable
internal data class GeminiErrorEnvelope(val error: GeminiErrorDetail? = null)

@Serializable
internal data class GeminiErrorDetail(val message: String? = null)

/** Google Gemini REST API (generateContent). */
internal object GeminiTransport : AiTransport {

    override suspend fun complete(request: AiRequest, apiKey: String, model: String): AiText = withContext(Dispatchers.IO) {
        try {
            val parts = request.images.map {
                GeminiPart(inlineData = GeminiInlineData(it.mimeType, Base64.getEncoder().encodeToString(it.bytes)))
            } + GeminiPart(text = request.prompt)
            val body = aiJson.encodeToString(
                GeminiRequest.serializer(),
                GeminiRequest(
                    contents = listOf(GeminiContent(parts)),
                    generationConfig = GeminiGenerationConfig(if (request.expectJson) "application/json" else "text/plain")
                )
            )
            // The key goes in a header rather than in the URL so it never ends up in logs.
            val response = postJson(
                url = "$GEMINI_ENDPOINT_BASE/$model:generateContent",
                body = body,
                headers = mapOf("x-goog-api-key" to apiKey),
                timeoutMillis = TIMEOUT_MILLIS
            )
            if (!response.isSuccessful) {
                val message = runCatching { aiJson.decodeFromString(GeminiErrorEnvelope.serializer(), response.body).error?.message }.getOrNull()
                return@withContext AiText.Error(AiErrors.http(AiProvider.GEMINI.shortName, response.code, message))
            }
            val decoded = aiJson.decodeFromString(GeminiResponse.serializer(), response.body)
            val candidate = decoded.candidates.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull { it.text != null }?.text
            if (text.isNullOrBlank()) {
                AiText.Error(describeIncomplete(candidate?.finishReason, decoded.promptFeedback?.blockReason))
            } else {
                AiText.Success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiText.Error(AiErrors.exception(e))
        }
    }

    /** Explains why Gemini returned no text: the prompt was blocked or the generation stopped early. */
    private fun describeIncomplete(finishReason: String?, blockReason: String?): String = ErrorDetail.markAsAi(
        when {
            blockReason != null -> L10n.str(R.string.gemini_blocked_x, blockReason)
            finishReason == "MAX_TOKENS" -> L10n.str(R.string.gemini_max_tokens)
            finishReason == "SAFETY" -> L10n.str(R.string.gemini_safety)
            finishReason == "RECITATION" -> L10n.str(R.string.gemini_recitation)
            finishReason != null -> L10n.str(R.string.gemini_incomplete_x, finishReason)
            else -> L10n.str(R.string.gemini_empty)
        }
    )
}
