package org.calamares.miga.data.ai

import kotlinx.serialization.json.Json
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.dictation.DictationCleanupClient
import org.calamares.miga.data.dictation.DictationCleanupResult
import org.calamares.miga.data.dictation.buildDictationCleanupPrompt
import org.calamares.miga.data.dictation.stripSurroundingQuotes
import org.calamares.miga.data.health.RecipeHealthClient
import org.calamares.miga.data.health.RecipeHealthResult
import org.calamares.miga.data.health.RecipeHealthResultDto
import org.calamares.miga.data.health.buildHealthPrompt
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.nutrition.RecipeNutritionClient
import org.calamares.miga.data.nutrition.RecipeNutritionResult
import org.calamares.miga.data.nutrition.RecipeNutritionResultDto
import org.calamares.miga.data.nutrition.buildNutritionPrompt
import org.calamares.miga.data.search.DishRecipeGenerationClient
import org.calamares.miga.data.search.DishSearchClient
import org.calamares.miga.data.search.DishSearchResult
import org.calamares.miga.data.search.DishSearchResultDto
import org.calamares.miga.data.search.DishSuggestion
import org.calamares.miga.data.search.RecipeUrlImportClient
import org.calamares.miga.data.search.buildDishRecipePrompt
import org.calamares.miga.data.search.buildDishSearchPrompt
import org.calamares.miga.data.search.buildUrlImportPrompt
import org.calamares.miga.data.substitution.IngredientSubstitution
import org.calamares.miga.data.substitution.IngredientSubstitutionClient
import org.calamares.miga.data.substitution.SubstitutionResult
import org.calamares.miga.data.substitution.SubstitutionResultDto
import org.calamares.miga.data.substitution.buildSubstitutionPrompt
import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.data.vision.RecipeVisionClient
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.RecipeVisionResultDto
import org.calamares.miga.data.vision.VisionImageInput
import org.calamares.miga.data.vision.recipeExtractionPrompt

// Implementaciones de cada función de IA contra OpenRouter. Usan los mismos prompts y DTOs que
// Gemini y Claude; solo cambia el transporte (ver [OpenRouterChat]).

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private const val RECIPE_MAX_TOKENS = 8192
private const val SHORT_MAX_TOKENS = 1024

/** Convierte la respuesta de una receta completa en [RecipeVisionResult]. */
private fun recipeResult(result: OpenRouterText, emptyMessage: () -> String): RecipeVisionResult = when (result) {
    is OpenRouterText.Error -> RecipeVisionResult.Error(result.reason)
    is OpenRouterText.Success -> try {
        val recipe = json.decodeFromString(RecipeVisionResultDto.serializer(), extractJsonObject(result.text))
        if (recipe.name.isBlank()) RecipeVisionResult.Error(emptyMessage()) else RecipeVisionResult.Success(recipe)
    } catch (e: Exception) {
        RecipeVisionResult.Error(AiErrors.badResponse(e, result.text))
    }
}

object OpenRouterVisionClient : RecipeVisionClient {
    override suspend fun extractRecipe(images: List<VisionImageInput>, apiKey: String, model: String): RecipeVisionResult {
        if (images.isEmpty()) return RecipeVisionResult.Error(L10n.str(R.string.there_no_photos_process))
        val result = OpenRouterChat.complete(recipeExtractionPrompt(), apiKey, model, RECIPE_MAX_TOKENS, images)
        return recipeResult(result) { L10n.str(R.string.no_recipe_was_found_photo) }
    }
}

object OpenRouterRecipeUrlImportClient : RecipeUrlImportClient {
    override suspend fun importFromUrl(url: String, pageText: String, apiKey: String, model: String): RecipeVisionResult {
        val result = OpenRouterChat.complete(buildUrlImportPrompt(url, pageText), apiKey, model, RECIPE_MAX_TOKENS)
        return recipeResult(result) { L10n.str(R.string.no_recipe_was_found_page) }
    }
}

object OpenRouterDishRecipeGenerationClient : DishRecipeGenerationClient {
    override suspend fun generateRecipe(dish: DishSuggestion, apiKey: String, model: String): RecipeVisionResult {
        val result = OpenRouterChat.complete(buildDishRecipePrompt(dish), apiKey, model, RECIPE_MAX_TOKENS)
        return recipeResult(result) { L10n.str(R.string.couldnt_generate_recipe) }
    }
}

object OpenRouterDishSearchClient : DishSearchClient {
    override suspend fun searchDishes(query: String, apiKey: String, model: String): DishSearchResult =
        when (val result = OpenRouterChat.complete(buildDishSearchPrompt(query), apiKey, model, 2048)) {
            is OpenRouterText.Error -> DishSearchResult.Error(result.reason)
            is OpenRouterText.Success -> try {
                val dto = json.decodeFromString(DishSearchResultDto.serializer(), extractJsonObject(result.text))
                DishSearchResult.Success(dto.dishes.filter { it.name.isNotBlank() }.map { DishSuggestion(it.name, it.description, it.origin) })
            } catch (e: Exception) {
                DishSearchResult.Error(AiErrors.badResponse(e, result.text))
            }
        }
}

object OpenRouterHealthClient : RecipeHealthClient {
    override suspend fun analyzeHealthiness(ingredientsText: String, stepsText: String, apiKey: String, model: String): RecipeHealthResult =
        when (val result = OpenRouterChat.complete(buildHealthPrompt(ingredientsText, stepsText), apiKey, model, SHORT_MAX_TOKENS)) {
            is OpenRouterText.Error -> RecipeHealthResult.Error(result.reason)
            is OpenRouterText.Success -> try {
                val dto = json.decodeFromString(RecipeHealthResultDto.serializer(), extractJsonObject(result.text))
                val level = runCatching { HealthColorLevel.valueOf(dto.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
                RecipeHealthResult.Success(level, dto.description)
            } catch (e: Exception) {
                RecipeHealthResult.Error(AiErrors.badResponse(e, result.text))
            }
        }
}

object OpenRouterNutritionClient : RecipeNutritionClient {
    override suspend fun analyzeNutrition(
        ingredientsText: String,
        stepsText: String,
        servings: Int,
        apiKey: String,
        model: String
    ): RecipeNutritionResult =
        when (val result = OpenRouterChat.complete(buildNutritionPrompt(ingredientsText, stepsText, servings), apiKey, model, SHORT_MAX_TOKENS)) {
            is OpenRouterText.Error -> RecipeNutritionResult.Error(result.reason)
            is OpenRouterText.Success -> try {
                val dto = json.decodeFromString(RecipeNutritionResultDto.serializer(), extractJsonObject(result.text))
                RecipeNutritionResult.Success(dto.caloriesPerServing, dto.proteinGrams, dto.carbsGrams, dto.fatGrams)
            } catch (e: Exception) {
                RecipeNutritionResult.Error(AiErrors.badResponse(e, result.text))
            }
        }
}

object OpenRouterIngredientSubstitutionClient : IngredientSubstitutionClient {
    override suspend fun suggestSubstitutes(ingredientName: String, recipeName: String, apiKey: String, model: String): SubstitutionResult =
        when (val result = OpenRouterChat.complete(buildSubstitutionPrompt(ingredientName, recipeName), apiKey, model, SHORT_MAX_TOKENS)) {
            is OpenRouterText.Error -> SubstitutionResult.Error(result.reason)
            is OpenRouterText.Success -> try {
                val dto = json.decodeFromString(SubstitutionResultDto.serializer(), extractJsonObject(result.text))
                val substitutions = dto.substitutions.filter { it.substitute.isNotBlank() }.map { IngredientSubstitution(it.substitute, it.notes) }
                if (substitutions.isEmpty()) {
                    SubstitutionResult.Error(L10n.str(R.string.no_substitutes_found_ingredient))
                } else {
                    SubstitutionResult.Success(substitutions)
                }
            } catch (e: Exception) {
                SubstitutionResult.Error(AiErrors.badResponse(e, result.text))
            }
        }
}

object OpenRouterDictationCleanupClient : DictationCleanupClient {
    override suspend fun cleanUp(rawText: String, apiKey: String, model: String): DictationCleanupResult =
        when (val result = OpenRouterChat.complete(buildDictationCleanupPrompt(rawText), apiKey, model, 512)) {
            is OpenRouterText.Error -> DictationCleanupResult.Error(result.reason)
            is OpenRouterText.Success -> DictationCleanupResult.Success(stripSurroundingQuotes(result.text))
        }
}
