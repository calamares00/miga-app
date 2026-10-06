package org.calamares.miga.data.nutrition

import kotlinx.serialization.Serializable
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.ai.outputLanguageInstruction
import org.calamares.miga.data.support.AiErrors

private const val NUTRITION_MAX_TOKENS = 1024

sealed interface RecipeNutritionResult {
    data class Success(val caloriesPerServing: Int, val proteinGrams: Double, val carbsGrams: Double, val fatGrams: Double) :
        RecipeNutritionResult
    data class Error(val reason: String) : RecipeNutritionResult
}

@Serializable
internal data class RecipeNutritionResultDto(
    val caloriesPerServing: Int = 0,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0
)

/** Estimates the approximate nutrition per serving from the ingredients, method and servings. */
suspend fun AiCandidate.analyzeNutrition(ingredientsText: String, stepsText: String, servings: Int): RecipeNutritionResult =
    when (val result = complete(AiRequest(buildNutritionPrompt(ingredientsText, stepsText, servings), NUTRITION_MAX_TOKENS))) {
        is AiText.Error -> RecipeNutritionResult.Error(result.reason)
        is AiText.Success -> try {
            val dto = decodeAiJson(RecipeNutritionResultDto.serializer(), result.text)
            RecipeNutritionResult.Success(dto.caloriesPerServing, dto.proteinGrams, dto.carbsGrams, dto.fatGrams)
        } catch (e: Exception) {
            RecipeNutritionResult.Error(AiErrors.badResponse(e, result.text))
        }
    }

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
