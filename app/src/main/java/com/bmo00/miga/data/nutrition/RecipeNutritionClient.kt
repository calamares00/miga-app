package com.bmo00.miga.data.nutrition

import com.bmo00.miga.data.vision.VisionProviderType
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
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class RecipeNutritionResultDto(
    val caloriesPerServing: Int = 0,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0
)

// Prompt compartido entre todos los proveedores: deben pedir exactamente el mismo criterio y
// formato, si no divergirían al cambiar de proveedor en Ajustes.
internal fun buildNutritionPrompt(ingredientsText: String, stepsText: String, servings: Int): String = """
Eres un asistente que estima la información nutricional aproximada de una receta a partir de sus
ingredientes, su forma de cocinado y el número de raciones.

Ingredientes (para $servings ración(es) en total):
$ingredientsText

Preparación:
$stepsText

Devuelve ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional, con la
estimación POR RACIÓN (dividiendo el total de la receta entre $servings, no el total):
{ "caloriesPerServing": entero, "proteinGrams": número, "carbsGrams": número, "fatGrams": número }
Es una estimación aproximada basada en ingredientes habituales, no un análisis exacto de
laboratorio; si algún ingrediente es ambiguo, usa una estimación razonable en vez de dejarlo en
blanco o en cero.
""".trimIndent()
