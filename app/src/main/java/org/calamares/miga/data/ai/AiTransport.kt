package org.calamares.miga.data.ai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
    AiProvider.OPENAI -> OpenAiTransport
}

/** Runs [request] against this candidate's provider, key and model. */
suspend fun AiCandidate.complete(request: AiRequest): AiText =
    transportFor(provider).complete(request, apiKey, model)

/**
 * Lenient JSON setup shared by every AI client. coerceInputValues turns a null sent for a
 * non-null field with a default value (models sometimes do that) into the default.
 */
internal val aiJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * Decodes the JSON object contained in a model answer, ignoring any text around it.
 *
 * Values are first adapted to the types [serializer] expects, because models do not always follow
 * the format: decimals in whole-number fields ("prepTimeMinutes": 1.25), numbers sent as text
 * ("quantity": "1/2"), a number where a text is expected or a nullable field left out.
 */
internal fun <T> decodeAiJson(serializer: KSerializer<T>, text: String): T {
    val tree = aiJson.parseToJsonElement(extractJsonObject(text))
    return aiJson.decodeFromJsonElement(serializer, coerceToDescriptor(tree, serializer.descriptor))
}

/** Rewrites [element] so its primitive values match the kinds described by [descriptor]. */
@OptIn(ExperimentalSerializationApi::class)
internal fun coerceToDescriptor(element: JsonElement, descriptor: SerialDescriptor): JsonElement =
    when (descriptor.kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> (element as? JsonObject)?.let { obj ->
            val fields = obj.mapValues { (key, value) ->
                val index = descriptor.getElementIndex(key)
                if (index == CompositeDecoder.UNKNOWN_NAME) value else coerceToDescriptor(value, descriptor.getElementDescriptor(index))
            }.toMutableMap()
            // A nullable field without a default that the model left out is read as null.
            for (index in 0 until descriptor.elementsCount) {
                val name = descriptor.getElementName(index)
                if (name !in fields && descriptor.getElementDescriptor(index).isNullable && !descriptor.isElementOptional(index)) {
                    fields[name] = JsonNull
                }
            }
            JsonObject(fields)
        } ?: element
        StructureKind.LIST -> (element as? JsonArray)?.let { array ->
            JsonArray(array.map { coerceToDescriptor(it, descriptor.getElementDescriptor(0)) })
        } ?: element
        PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE ->
            coerceNumber(element, descriptor) { value -> JsonPrimitive(Math.round(value)) }
        PrimitiveKind.DOUBLE, PrimitiveKind.FLOAT ->
            coerceNumber(element, descriptor) { value -> JsonPrimitive(value) }
        PrimitiveKind.STRING -> (element as? JsonPrimitive)?.takeIf { !it.isString && element !is JsonNull }
            ?.let { JsonPrimitive(it.content) } ?: element
        else -> element
    }

/**
 * Turns a numeric value sent as a decimal or as text into [build]'s number. Text that is not a
 * number becomes null when the field allows it and is left untouched otherwise.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun coerceNumber(element: JsonElement, descriptor: SerialDescriptor, build: (Double) -> JsonPrimitive): JsonElement {
    val primitive = element as? JsonPrimitive ?: return element
    if (primitive is JsonNull) return element
    val value = parseLooseNumber(primitive.content)
    return when {
        value != null && value.isFinite() -> build(value)
        descriptor.isNullable -> JsonNull
        else -> element
    }
}

/** Parses "12", "1.5", "1,5", "1/2" or "1 1/2"; null for anything else. */
internal fun parseLooseNumber(text: String): Double? {
    val trimmed = text.trim().replace(',', '.')
    trimmed.toDoubleOrNull()?.let { return it }
    val mixed = Regex("""^(\d+)\s+(\d+)/(\d+)$""").matchEntire(trimmed)
    if (mixed != null) {
        val (whole, numerator, denominator) = mixed.destructured
        if (denominator.toDouble() != 0.0) return whole.toDouble() + numerator.toDouble() / denominator.toDouble()
    }
    val fraction = Regex("""^(\d+(?:\.\d+)?)/(\d+(?:\.\d+)?)$""").matchEntire(trimmed)
    if (fraction != null) {
        val (numerator, denominator) = fraction.destructured
        if (denominator.toDouble() != 0.0) return numerator.toDouble() / denominator.toDouble()
    }
    return null
}

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

/**
 * JSON POST that stops as soon as the calling coroutine is cancelled: the user left the screen or
 * cancelled the task. The request runs on an IO thread and cancelling closes its connection, which
 * ends the blocked read at once; otherwise the task (and its "AI is working" notification) would
 * stay until the server answered, up to the timeout. Network failures are thrown as IOException.
 */
internal suspend fun postJson(url: String, body: String, headers: Map<String, String>, timeoutMillis: Int): HttpResponse =
    suspendCancellableCoroutine { continuation ->
        val connection = URL(url).openConnection() as HttpURLConnection
        continuation.invokeOnCancellation { runCatching { connection.disconnect() } }
        HTTP_EXECUTOR.execute {
            val result = runCatching {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = timeoutMillis
                connection.readTimeout = timeoutMillis
                connection.setRequestProperty("Content-Type", "application/json")
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            }
            connection.disconnect()
            // After a cancellation the continuation is already resumed; these are then ignored.
            if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
        }
    }

private val HTTP_EXECUTOR = Dispatchers.IO.asExecutor()

/** Blocking GET; call it from Dispatchers.IO. Network failures are thrown as IOException. */
internal fun getJson(url: String, headers: Map<String, String>, timeoutMillis: Int): HttpResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    } finally {
        connection.disconnect()
    }
}
