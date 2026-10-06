package org.calamares.miga.data.ai

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** An image already in memory, ready to be sent to a model that can read images. */
class AiImage(val bytes: ByteArray, val mimeType: String)

/**
 * A single-turn request to a model.
 *
 * @param expectJson whether the prompt asks for a JSON object; providers with a JSON output mode
 *   (Gemini) use it to get a cleaner answer.
 */
data class AiRequest(
    val prompt: String,
    val maxTokens: Int,
    val images: List<AiImage> = emptyList(),
    val expectJson: Boolean = true
)

/** Raw text answer of a model, or a user-facing error built with [org.calamares.miga.data.support.AiErrors]. */
sealed interface AiText {
    data class Success(val text: String) : AiText
    data class Error(val reason: String) : AiText
}

/** Sends an [AiRequest] to one provider's API and returns the model's text. Never throws. */
internal interface AiTransport {
    suspend fun complete(request: AiRequest, apiKey: String, model: String): AiText
}

private fun transportFor(provider: AiProvider): AiTransport = when (provider) {
    AiProvider.GEMINI -> GeminiTransport
    AiProvider.ANTHROPIC -> AnthropicTransport
    AiProvider.OPENROUTER -> OpenRouterTransport
}

/** Runs [request] against this candidate's provider, key and model. */
suspend fun AiCandidate.complete(request: AiRequest): AiText =
    transportFor(provider).complete(request, apiKey, model)

/**
 * Lenient JSON setup shared by every AI client. coerceInputValues turns a null sent for a
 * non-null field with a default value (models sometimes do that) into the default.
 */
internal val aiJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Decodes the JSON object contained in a model answer, ignoring any text around it. */
internal fun <T> decodeAiJson(serializer: KSerializer<T>, text: String): T =
    aiJson.decodeFromString(serializer, extractJsonObject(text))

/**
 * Returns the outermost `{...}` block of [raw]. Models sometimes wrap the JSON in Markdown fences
 * or add a sentence before or after it even when asked for JSON only.
 */
internal fun extractJsonObject(raw: String): String {
    val trimmed = raw.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
}

/** Status code and body (the error body for non-2xx answers) of an HTTP call. */
internal class HttpResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Blocking JSON POST; call it from Dispatchers.IO. Network failures are thrown as IOException. */
internal fun postJson(url: String, body: String, headers: Map<String, String>, timeoutMillis: Int): HttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    } finally {
        connection.disconnect()
    }
}
