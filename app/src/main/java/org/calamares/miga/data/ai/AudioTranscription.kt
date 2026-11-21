package org.calamares.miga.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.io.ReadLimits
import org.calamares.miga.data.io.readTextAtMost
import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.data.support.AppDispatchers
import org.calamares.miga.data.support.ErrorDetail
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How a provider can take the sound of a video. */
enum class AudioSupport {
    /** The model listens to the audio itself (Gemini). */
    LISTENS,

    /** The provider has a speech-to-text service; its chat model then reads the text. */
    TRANSCRIBES
}

/** Null for providers that can do neither (Claude, DeepSeek, OpenRouter, a custom server). */
val AiCandidate.audioSupport: AudioSupport?
    get() = when (provider) {
        AiProvider.GEMINI -> AudioSupport.LISTENS
        AiProvider.OPENAI, AiProvider.GROQ, AiProvider.MISTRAL -> AudioSupport.TRANSCRIBES
        else -> null
    }

/** Speech-to-text address and model of each provider that has one (OpenAI's format in all three). */
internal fun transcriptionService(provider: AiProvider): Pair<String, String>? = when (provider) {
    AiProvider.OPENAI -> "https://api.openai.com/v1/audio/transcriptions" to "gpt-4o-mini-transcribe"
    AiProvider.GROQ -> "https://api.groq.com/openai/v1/audio/transcriptions" to "whisper-large-v3-turbo"
    AiProvider.MISTRAL -> "https://api.mistral.ai/v1/audio/transcriptions" to "voxtral-mini-latest"
    else -> null
}

private const val TRANSCRIPTION_TIMEOUT_MILLIS = 180_000

@Serializable
internal data class TranscriptionResponse(val text: String = "")

/**
 * Turns [m4a] (an audio track in an MP4 file) into text with this provider's speech-to-text
 * service. Errors are AI errors, so another provider is tried.
 */
suspend fun AiCandidate.transcribe(m4a: ByteArray): AiText = withContext(AppDispatchers.io) {
    val (endpoint, model) = transcriptionService(provider)
        ?: return@withContext AiText.Error(ErrorDetail.markAsAi(L10n.str(R.string.ai_no_transcription, provider.shortName)))
    try {
        val boundary = "miga-" + UUID.randomUUID().toString()
        val body = multipartBody(boundary, mapOf("model" to model), "file", "audio.m4a", "audio/mp4", m4a)
        val response = postMultipart(endpoint, boundary, body, mapOf("Authorization" to "Bearer $apiKey"))
        if (!response.isSuccessful) {
            val message = runCatching { aiJson.decodeFromString(OpenAiErrorEnvelope.serializer(), response.body).error?.message }.getOrNull()
            return@withContext AiText.Error(AiErrors.http(provider.shortName, response.code, message ?: response.body.take(1500), response.retryAfter))
        }
        val text = runCatching { aiJson.decodeFromString(TranscriptionResponse.serializer(), response.body).text }.getOrNull()
            ?: return@withContext AiText.Error(AiErrors.badResponse(IllegalStateException("No transcription"), response.body))
        AiText.Success(text.trim())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        coroutineContext.ensureActive()
        AiText.Error(AiErrors.exception(e))
    }
}

/** A multipart/form-data body with text [fields] and one file. */
internal fun multipartBody(
    boundary: String,
    fields: Map<String, String>,
    fileField: String,
    fileName: String,
    fileType: String,
    file: ByteArray
): ByteArray {
    val out = ByteArrayOutputStream()
    fun line(text: String = "") = out.write("$text\r\n".toByteArray())
    fields.forEach { (name, value) ->
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"$name\"")
        line()
        line(value)
    }
    line("--$boundary")
    line("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"")
    line("Content-Type: $fileType")
    line()
    out.write(file)
    line()
    line("--$boundary--")
    return out.toByteArray()
}

/** Like postJson, for a multipart body: stops as soon as the calling coroutine is cancelled. */
private suspend fun postMultipart(url: String, boundary: String, body: ByteArray, headers: Map<String, String>): HttpResponse =
    suspendCancellableCoroutine { continuation ->
        val connection = URL(url).openConnection() as HttpURLConnection
        continuation.invokeOnCancellation { runCatching { connection.disconnect() } }
        HTTP_EXECUTOR.execute {
            val result = runCatching {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = TRANSCRIPTION_TIMEOUT_MILLIS
                connection.readTimeout = TRANSCRIPTION_TIMEOUT_MILLIS
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                HttpResponse(code, stream?.use { it.readTextAtMost(ReadLimits.API_JSON_BYTES) }.orEmpty(), connection.getHeaderField("Retry-After"))
            }
            connection.disconnect()
            if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
        }
    }
