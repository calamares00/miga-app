package org.calamares.miga.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.data.support.ErrorDetail
import org.calamares.miga.data.vision.VisionImageInput
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

private const val OPENROUTER_CHAT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
private const val OPENROUTER_MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models"
// Los modelos gratuitos de OpenRouter suelen ir más lentos que las APIs directas.
private const val TIMEOUT_MILLIS = 60000
private const val PROVIDER_NAME = "OpenRouter"

// --- DTOs de la API de OpenRouter (compatible con la de chat completions de OpenAI) ---

@Serializable
internal data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    // Opcional: si un proveedor rechaza el límite pedido, se reintenta sin él (ver complete()).
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
internal data class OpenRouterMessage(val role: String = "user", val content: List<OpenRouterPart>)

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

/** Cuando el fallo viene del proveedor final, OpenRouter solo dice "Provider returned error" y
 *  deja el motivo real en `metadata.raw` (texto o JSON) junto con el nombre del proveedor. */
@Serializable
internal data class OpenRouterErrorMetadata(
    val raw: JsonElement? = null,
    @SerialName("provider_name") val providerName: String? = null
)

/** Mensaje del error con el motivo real del proveedor final, si lo hay. */
internal fun OpenRouterError.describe(): String? {
    val raw = metadata?.raw?.let { element ->
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: element.toString()
    }?.take(1500)
    return listOfNotNull(
        message,
        metadata?.providerName?.let { "Provider: $it" },
        raw
    ).joinToString("\n").ifBlank { null }
}

@Serializable
internal data class OpenRouterErrorEnvelope(val error: OpenRouterError? = null)

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

internal sealed interface OpenRouterText {
    data class Success(val text: String) : OpenRouterText
    data class Error(val reason: String) : OpenRouterText
}

/** Llamada genérica a OpenRouter: un único mensaje de usuario con texto y, opcionalmente, imágenes. */
internal object OpenRouterChat {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun complete(
        prompt: String,
        apiKey: String,
        model: String,
        maxTokens: Int,
        images: List<VisionImageInput> = emptyList()
    ): OpenRouterText = withContext(Dispatchers.IO) {
        try {
            val parts = images.map {
                OpenRouterPart(
                    type = "image_url",
                    imageUrl = OpenRouterImageUrl("data:${it.mimeType};base64," + Base64.getEncoder().encodeToString(it.bytes))
                )
            } + OpenRouterPart(type = "text", text = prompt)
            val first = post(OpenRouterRequest(model = model, messages = listOf(OpenRouterMessage(content = parts)), maxTokens = maxTokens), apiKey)
            // Un 400 suele significar que el proveedor final no acepta algún parámetro; el más
            // habitual con los modelos gratuitos es un max_tokens mayor que su límite de salida.
            // Se reintenta una vez sin él (el proveedor aplica su propio máximo).
            val result = if (first is HttpOutcome.Failed && first.code == 400) {
                post(OpenRouterRequest(model = model, messages = listOf(OpenRouterMessage(content = parts))), apiKey)
                    .let { retry -> if (retry is HttpOutcome.Failed) first.copy(detail = listOfNotNull(first.detail, "— sin max_tokens:", retry.detail).joinToString("\n")) else retry }
            } else {
                first
            }
            when (result) {
                is HttpOutcome.Failed -> OpenRouterText.Error(AiErrors.http(PROVIDER_NAME, result.code, result.detail))
                is HttpOutcome.Ok -> {
                    val response = json.decodeFromString(OpenRouterResponse.serializer(), result.body)
                    // OpenRouter puede devolver 200 con un error del proveedor final dentro del cuerpo.
                    val error = response.error
                    if (error != null) {
                        OpenRouterText.Error(AiErrors.http(PROVIDER_NAME, error.code ?: 502, error.describe()))
                    } else {
                        val choice = response.choices.firstOrNull()
                        val text = choice?.message?.content?.takeIf { it.isNotBlank() }
                        if (text != null) OpenRouterText.Success(text) else OpenRouterText.Error(describeIncomplete(choice?.finishReason))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpenRouterText.Error(AiErrors.exception(e))
        }
    }

    private sealed interface HttpOutcome {
        data class Ok(val body: String) : HttpOutcome
        data class Failed(val code: Int, val detail: String?) : HttpOutcome
    }

    private fun post(request: OpenRouterRequest, apiKey: String): HttpOutcome {
        val requestBody = json.encodeToString(OpenRouterRequest.serializer(), request)
        val connection = URL(OPENROUTER_CHAT_ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        // Atribución opcional que pide OpenRouter (aparece en sus estadísticas de apps).
        connection.setRequestProperty("HTTP-Referer", "https://miga.calamares.org")
        connection.setRequestProperty("X-Title", "Miga")
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        try {
            connection.outputStream.use { it.write(requestBody.toByteArray()) }
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                val detail = errorBody?.let {
                    runCatching { json.decodeFromString(OpenRouterErrorEnvelope.serializer(), it).error?.describe() }.getOrNull()
                        ?: it.take(1500)
                }
                return HttpOutcome.Failed(responseCode, detail)
            }
            return HttpOutcome.Ok(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun describeIncomplete(finishReason: String?): String = ErrorDetail.markAsAi(
        when (finishReason) {
            "length" -> L10n.str(R.string.ai_incomplete_too_long, PROVIDER_NAME)
            "content_filter" -> L10n.str(R.string.ai_incomplete_refused, PROVIDER_NAME)
            null -> L10n.str(R.string.ai_incomplete_empty, PROVIDER_NAME)
            else -> L10n.str(R.string.ai_incomplete_other, PROVIDER_NAME, finishReason)
        }
    )
}

/** Modelo del catálogo de OpenRouter tal como se muestra en Ajustes. */
data class OpenRouterModel(val id: String, val name: String, val isFree: Boolean, val supportsImages: Boolean)

/**
 * Catálogo público de modelos de OpenRouter (no necesita clave). Se descarga al abrir el selector
 * y se guarda en memoria mientras la app esté abierta.
 */
object OpenRouterModels {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val mutex = Mutex()
    private var cache: List<OpenRouterModel>? = null

    /** Lista de modelos (gratuitos primero) o null si no se ha podido descargar. */
    suspend fun fetch(forceRefresh: Boolean = false): List<OpenRouterModel>? = mutex.withLock {
        if (!forceRefresh) cache?.let { return@withLock it }
        val fetched = withContext(Dispatchers.IO) {
            try {
                val connection = URL(OPENROUTER_MODELS_ENDPOINT).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    json.decodeFromString(OpenRouterModelsResponse.serializer(), body).data
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
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        if (fetched != null) cache = fetched
        fetched ?: cache
    }

    /** Lo último descargado, sin red (para pintar etiquetas de un modelo ya elegido). */
    fun cached(): List<OpenRouterModel>? = cache
}

/** Un modelo es gratuito si su id lleva el sufijo ":free" o si cuesta 0 por token de entrada y de salida. */
internal fun isFreeModel(id: String, promptPrice: String?, completionPrice: String?): Boolean =
    id.endsWith(":free") ||
        (promptPrice?.toDoubleOrNull() == 0.0 && completionPrice?.toDoubleOrNull() == 0.0)

/**
 * Algunos modelos (sobre todo los gratuitos) añaden texto antes o después del JSON pese a pedir
 * solo JSON; nos quedamos con el primer objeto `{...}` completo.
 */
internal fun extractJsonObject(raw: String): String {
    val trimmed = raw.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
}
