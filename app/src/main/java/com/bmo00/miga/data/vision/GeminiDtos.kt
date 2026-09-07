package com.bmo00.miga.data.vision

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de la API REST de Gemini (generateContent), compartidos entre [GeminiVisionClient] y
 * el cliente de valoración de salud (`data/health/GeminiHealthClient.kt`) — son agnósticos del
 * caso de uso, la única diferencia entre una llamada de visión y una de solo texto es si se
 * rellena [GeminiPart.inlineData] o [GeminiPart.text].
 */
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
internal data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String
)

@Serializable
internal data class GeminiGenerationConfig(val responseMimeType: String = "application/json")

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

// Mensaje legible cuando Gemini no ha devuelto texto: o bien la generación se cortó antes de
// terminar (candidate.finishReason) o bien toda la respuesta se bloqueó de entrada
// (promptFeedback.blockReason) — sin esto, el llamante solo podría mostrar un "no hay resultado"
// genérico sin explicar por qué. Compartida entre GeminiVisionClient y GeminiHealthClient.
internal fun describeGeminiIncompleteResponse(finishReason: String?, blockReason: String?): String = when {
    blockReason != null -> "Gemini bloqueó la respuesta por su política de contenido ($blockReason)."
    finishReason == "MAX_TOKENS" -> "Gemini cortó la respuesta antes de terminar (demasiado larga). Prueba con menos fotos a la vez o una foto más sencilla."
    finishReason == "SAFETY" -> "Gemini bloqueó la respuesta por su política de contenido."
    finishReason == "RECITATION" -> "Gemini bloqueó la respuesta por posible contenido protegido."
    finishReason != null -> "Gemini no completó la respuesta (motivo: $finishReason)."
    else -> "Gemini no devolvió ningún resultado."
}
