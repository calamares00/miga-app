package com.bmo00.miga.data.vision

import com.bmo00.miga.data.export.IngredientGroupDto
import com.bmo00.miga.data.export.StepGroupDto
import kotlinx.serialization.Serializable

/**
 * Receta reconocida a partir de una foto. Es un DTO propio, independiente de [com.bmo00.miga.
 * data.export.RecipeExportDto] (que tiene su propio versionado de esquema para export/import):
 * aquí no hace falta "uid" ni "recipeBookName" ni "photos", ese contexto lo aporta la pantalla
 * que recibe el resultado. Reutiliza [IngredientGroupDto]/[StepGroupDto] porque su forma es
 * exactamente la que necesita un LLM al describir ingredientes y pasos.
 */
@Serializable
data class RecipeVisionResultDto(
    val name: String,
    val categoryName: String? = null,
    val difficulty: String = "MEDIA",
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int = 4,
    val notes: String = "",
    val source: String = "",
    val ingredientGroups: List<IngredientGroupDto> = emptyList(),
    val stepGroups: List<StepGroupDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val utensils: List<String> = emptyList()
)

sealed interface RecipeVisionResult {
    data class Success(val recipe: RecipeVisionResultDto) : RecipeVisionResult
    data class Error(val reason: String) : RecipeVisionResult
}

/** Reconoce y estructura el texto de la foto de una receta usando un LLM con visión. */
interface RecipeVisionClient {
    suspend fun extractRecipe(imageBytes: ByteArray, mimeType: String, apiKey: String, model: String): RecipeVisionResult
}

/** Único proveedor implementado por ahora; añadir uno nuevo es solo un caso más aquí. */
fun visionClientFor(provider: VisionProviderType): RecipeVisionClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiVisionClient
}
