package com.bmo00.miga.data.health

import com.bmo00.miga.data.model.HealthColorLevel
import com.bmo00.miga.data.vision.VisionProviderType
import kotlinx.serialization.Serializable

sealed interface RecipeHealthResult {
    data class Success(val colorLevel: HealthColorLevel, val description: String) : RecipeHealthResult
    data class Error(val reason: String) : RecipeHealthResult
}

/** Analiza lo saludable que es una receta a partir de sus ingredientes y su forma de cocinado. */
interface RecipeHealthClient {
    suspend fun analyzeHealthiness(ingredientsText: String, stepsText: String, apiKey: String, model: String): RecipeHealthResult
}

fun healthClientFor(provider: VisionProviderType): RecipeHealthClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiHealthClient
    VisionProviderType.ANTHROPIC -> AnthropicHealthClient
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class RecipeHealthResultDto(val colorLevel: String = "YELLOW", val description: String = "")

// Prompt compartido entre todos los proveedores: deben pedir exactamente el mismo criterio y
// formato, si no divergirían al cambiar de proveedor en Ajustes.
internal fun buildHealthPrompt(ingredientsText: String, stepsText: String): String = """
Eres un asistente que evalúa lo saludable que es una receta a partir de sus ingredientes y su
forma de cocinado.

Ingredientes:
$ingredientsText

Preparación:
$stepsText

Devuelve ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional:
{ "colorLevel": "GREEN" | "YELLOW" | "RED", "description": "string, 2-4 frases explicando por qué" }
GREEN = receta equilibrada y saludable. YELLOW = moderada (algún exceso de grasa, azúcar o sal,
procesados, fritos ocasionales). RED = poco saludable (frituras, mucho azúcar o grasa saturada,
ultraprocesados, sin verdura ni proteína magra). Basa el análisis solo en lo indicado, sin inventar
datos nutricionales exactos.
""".trimIndent()
