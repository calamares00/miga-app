package org.calamares.miga.data.nutrition

import org.calamares.miga.data.ai.OpenRouterNutritionClient
import org.calamares.miga.data.vision.outputLanguageInstruction
import org.calamares.miga.data.vision.VisionProviderType
import kotlinx.serialization.Serializable

sealed interface RecipeNutritionResult {
    data class Success(val caloriesPerServing: Int, val proteinGrams: Double, val carbsGrams: Double, val fatGrams: Double) :
        RecipeNutritionResult
    data class Error(val reason: String) : RecipeNutritionResult
}

/** Estima la información nutricional aproximada de una receta (por ración) a partir de sus
 *  ingredientes, su forma de cocinado y el número de raciones. */
interface RecipeNutritionClient {
    suspend fun analyzeNutrition(ingredientsText: String, stepsText: String, servings: Int, apiKey: String, model: String): RecipeNutritionResult
}

fun nutritionClientFor(provider: VisionProviderType): RecipeNutritionClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiNutritionClient
    VisionProviderType.ANTHROPIC -> AnthropicNutritionClient
    VisionProviderType.OPENROUTER -> OpenRouterNutritionClient
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class RecipeNutritionResultDto(
    val caloriesPerServing: Int = 0,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0
)

/** Prompt shared by every provider so the criteria and format never diverge. */
internal fun buildNutritionPrompt(ingredientsText: String, stepsText: String, servings: Int): String = """
You are an assistant that estimates the approximate nutritional information of a recipe from its
ingredients, how it is cooked and the number of servings.

Ingredients (for $servings serving(s) in total):
$ingredientsText

Method:
$stepsText

Return ONLY a JSON object with exactly this format, with no explanations or extra text, with the
estimate PER SERVING (the recipe total divided by $servings, not the total):
{ "caloriesPerServing": integer, "proteinGrams": number, "carbsGrams": number, "fatGrams": number }
This is a rough estimate based on typical ingredients, not an exact lab analysis; if an ingredient
is ambiguous, use a reasonable estimate instead of leaving it blank or at zero.
""".trimIndent() + outputLanguageInstruction()
