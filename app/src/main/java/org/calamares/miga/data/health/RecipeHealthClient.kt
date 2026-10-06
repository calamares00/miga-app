package org.calamares.miga.data.health

import org.calamares.miga.data.ai.OpenRouterHealthClient
import org.calamares.miga.data.vision.outputLanguageInstruction
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.vision.VisionProviderType
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
    VisionProviderType.OPENROUTER -> OpenRouterHealthClient
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class RecipeHealthResultDto(val colorLevel: String = "YELLOW", val description: String = "")

/** Prompt shared by every provider so the criteria and format never diverge. */
internal fun buildHealthPrompt(ingredientsText: String, stepsText: String): String = """
You are an assistant that rates how healthy a recipe is based on its ingredients and how it is cooked.

Ingredients:
$ingredientsText

Method:
$stepsText

Return ONLY a JSON object with exactly this format, with no explanations or extra text:
{ "colorLevel": "GREEN" | "YELLOW" | "RED", "description": "string, 2-4 sentences explaining why" }
GREEN = balanced and healthy recipe. YELLOW = moderate (some excess fat, sugar or salt, processed
foods, occasional frying). RED = unhealthy (deep frying, lots of sugar or saturated fat,
ultra-processed foods, no vegetables or lean protein). Base the analysis only on what is given and
do not make up exact nutritional figures.
""".trimIndent() + outputLanguageInstruction()
