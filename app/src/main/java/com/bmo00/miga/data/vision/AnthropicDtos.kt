package com.bmo00.miga.data.vision

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de la API de Mensajes de Anthropic (Claude), compartidos entre [AnthropicVisionClient] y
 * el cliente de valoración de salud (`data/health/AnthropicHealthClient.kt`) — mismo espíritu que
 * [GeminiRequest]/[GeminiResponse]: agnósticos del caso de uso, la única diferencia entre una
 * llamada de visión y una de solo texto es si se incluyen bloques de tipo "image" además de "text".
 */
@Serializable
internal data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>
)

@Serializable
internal data class AnthropicMessage(val role: String = "user", val content: List<AnthropicContentBlock>)

@Serializable
internal data class AnthropicContentBlock(
    val type: String,
    val text: String? = null,
    val source: AnthropicImageSource? = null
)

@Serializable
internal data class AnthropicImageSource(
    val type: String = "base64",
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

// Mensaje legible cuando Claude no ha devuelto texto: la generación se cortó antes de terminar
// (normalmente por el límite de max_tokens) o la petición fue rechazada por política de
// contenido. Compartida entre AnthropicVisionClient y AnthropicHealthClient, mismo espíritu que
// describeGeminiIncompleteResponse.
internal fun describeAnthropicIncompleteResponse(stopReason: String?): String = when (stopReason) {
    "max_tokens" -> "Claude cortó la respuesta antes de terminar (demasiado larga). Prueba con menos fotos a la vez o una foto más sencilla."
    "refusal" -> "Claude rechazó la petición por su política de contenido."
    null -> "Claude no devolvió ningún resultado."
    else -> "Claude no completó la respuesta (motivo: $stopReason)."
}
