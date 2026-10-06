package org.calamares.miga.data.health

import kotlinx.serialization.Serializable
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.ai.outputLanguageInstruction
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.support.AiErrors

private const val HEALTH_MAX_TOKENS = 1024

sealed interface RecipeHealthResult {
    data class Success(val colorLevel: HealthColorLevel, val description: String) : RecipeHealthResult
    data class Error(val reason: String) : RecipeHealthResult
}

@Serializable
internal data class RecipeHealthResultDto(val colorLevel: String = "YELLOW", val description: String = "")

/** Rates how healthy a recipe is from its ingredients and cooking method. */
suspend fun AiCandidate.analyzeHealthiness(ingredientsText: String, stepsText: String): RecipeHealthResult =
    when (val result = complete(AiRequest(buildHealthPrompt(ingredientsText, stepsText), HEALTH_MAX_TOKENS))) {
        is AiText.Error -> RecipeHealthResult.Error(result.reason)
        is AiText.Success -> try {
            val dto = decodeAiJson(RecipeHealthResultDto.serializer(), result.text)
            val level = runCatching { HealthColorLevel.valueOf(dto.colorLevel.uppercase()) }.getOrDefault(HealthColorLevel.YELLOW)
            RecipeHealthResult.Success(level, dto.description)
        } catch (e: Exception) {
            RecipeHealthResult.Error(AiErrors.badResponse(e, result.text))
        }
    }

internal fun buildHealthPrompt(ingredientsText: String, stepsText: String): String = """
You are an assistant that rates how healthy a recipe is based on its ingredients and how it is cooked.

Ingredients:
$ingredientsText

Method:
$stepsText

Return ONLY a compact JSON object (no indentation or line breaks) with exactly this format, with no explanations or extra text:
{ "colorLevel": "GREEN" | "YELLOW" | "RED", "description": "string, 2-4 sentences explaining why" }
GREEN = balanced and healthy recipe. YELLOW = moderate (some excess fat, sugar or salt, processed
foods, occasional frying). RED = unhealthy (deep frying, lots of sugar or saturated fat,
ultra-processed foods, no vegetables or lean protein). Base the analysis only on what is given and
do not make up exact nutritional figures.
""".trimIndent() + outputLanguageInstruction()
