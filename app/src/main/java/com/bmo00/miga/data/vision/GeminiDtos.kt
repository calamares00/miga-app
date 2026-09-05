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
internal data class GeminiContent(val parts: List<GeminiPart>)

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
internal data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
internal data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
internal data class GeminiErrorEnvelope(val error: GeminiErrorDetail? = null)

@Serializable
internal data class GeminiErrorDetail(val message: String? = null)
