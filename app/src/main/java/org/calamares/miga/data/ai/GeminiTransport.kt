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

private const val GEMINI_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
private const val TIMEOUT_MILLIS = 60_000

// Listening to a video's audio takes longer than reading text.
private const val AUDIO_TIMEOUT_MILLIS = 180_000
private const val VIDEO_TIMEOUT_MILLIS = 300_000

@Serializable
internal data class GeminiRequest(val contents: List<GeminiContent>, val generationConfig: GeminiGenerationConfig)

@Serializable
internal data class GeminiContent(val parts: List<GeminiPart> = emptyList())

@Serializable
internal data class GeminiPart(
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null,
    @SerialName("file_data") val fileData: GeminiFileData? = null,
    val text: String? = null
)

/** A file the model fetches itself: Gemini takes public YouTube videos by their address. */
@Serializable
internal data class GeminiFileData(@SerialName("file_uri") val fileUri: String)

@Serializable
internal data class GeminiInlineData(@SerialName("mime_type") val mimeType: String, val data: String)

/** Fields with a default value are only encoded when marked with @EncodeDefault. */
@Serializable
internal data class GeminiGenerationConfig(
    @EncodeDefault val responseMimeType: String = "application/json",
    /** Fewer tokens per video frame: enough to read on-screen text, and a long video fits. */
    val mediaResolution: String? = null
)

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

    override suspend fun complete(request: AiRequest, apiKey: String, model: String): AiText = withContext(AppDispatchers.io) {
        try {
            val parts = request.images.map {
                GeminiPart(inlineData = GeminiInlineData(it.mimeType, Base64.getEncoder().encodeToString(it.bytes)))
            } + listOfNotNull(request.audio?.let {
                GeminiPart(inlineData = GeminiInlineData(it.mimeType, Base64.getEncoder().encodeToString(it.bytes)))
            }) + listOfNotNull(request.videoUrl?.let { GeminiPart(fileData = GeminiFileData(it)) }) + GeminiPart(text = request.prompt)
            val body = aiJson.encodeToString(
                GeminiRequest.serializer(),
                GeminiRequest(
                    contents = listOf(GeminiContent(parts)),
                    generationConfig = GeminiGenerationConfig(
                        if (request.expectJson) "application/json" else "text/plain",
                        mediaResolution = if (request.videoUrl != null) "MEDIA_RESOLUTION_LOW" else null
                    )
                )
            )
            // The key goes in a header rather than in the URL so it never ends up in logs.
            val response = postJson(
                url = "$GEMINI_ENDPOINT_BASE/$model:generateContent",
                body = body,
                headers = mapOf("x-goog-api-key" to apiKey),
                timeoutMillis = when {
                    request.videoUrl != null -> VIDEO_TIMEOUT_MILLIS
                    request.audio != null -> AUDIO_TIMEOUT_MILLIS
                    else -> TIMEOUT_MILLIS
                }
            )
            if (!response.isSuccessful) {
                val message = runCatching { aiJson.decodeFromString(GeminiErrorEnvelope.serializer(), response.body).error?.message }.getOrNull()
                return@withContext AiText.Error(AiErrors.http(AiProvider.GEMINI.shortName, response.code, message, response.retryAfter))
            }
            val decoded = aiJson.decodeFromString(GeminiResponse.serializer(), response.body)
            val candidate = decoded.candidates.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull { it.text != null }?.text
            // An answer cut off by the output limit is unusable (half a JSON), so it counts as a failure.
            if (text.isNullOrBlank() || candidate?.finishReason == "MAX_TOKENS") {
                AiText.Error(describeIncomplete(candidate?.finishReason, decoded.promptFeedback?.blockReason))
            } else {
                AiText.Success(text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
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
