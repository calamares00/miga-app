package com.bmo00.miga.data.search

import com.bmo00.miga.data.vision.RecipeVisionResult
import com.bmo00.miga.data.vision.VisionProviderType

/** Genera una receta completa (mismo formato que el reconocimiento por foto, ver
 *  [com.bmo00.miga.data.vision.RecipeVisionResultDto]) para un plato ya elegido, a partir de su
 *  nombre y descripción - sin ninguna foto de por medio, el LLM la genera desde su propio
 *  conocimiento. */
interface DishRecipeGenerationClient {
    suspend fun generateRecipe(dish: DishSuggestion, apiKey: String, model: String): RecipeVisionResult
}

fun dishRecipeGenerationClientFor(provider: VisionProviderType): DishRecipeGenerationClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiDishRecipeGenerationClient
    VisionProviderType.ANTHROPIC -> AnthropicDishRecipeGenerationClient
}

// Prompt compartido entre todos los proveedores: pide exactamente el mismo JSON que ya usa
// RECIPE_EXTRACTION_PROMPT (mismo RecipeVisionResultDto), para no tener el formato duplicado en dos
// sitios con riesgo de que diverjan; solo cambia la instrucción de partida (generar en vez de
// transcribir una foto).
internal fun buildDishRecipePrompt(dish: DishSuggestion): String {
    val originText = dish.origin?.takeIf { it.isNotBlank() }?.let { " (típico de $it)" }.orEmpty()
    val contextText = dish.description.takeIf { it.isNotBlank() }?.let { "\nContexto: $it" }.orEmpty()
    return """
Eres un asistente de cocina. Genera una receta completa, realista y bien explicada para el plato
"${dish.name}"$originText.$contextText

Devuelve ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional:
{
  "name": "string",
  "categoryName": "string o null",
  "difficulty": "FACIL" | "MEDIA" | "DIFICIL",
  "prepTimeMinutes": number o null,
  "cookTimeMinutes": number o null,
  "servings": number,
  "notes": "string",
  "source": "string",
  "ingredientGroups": [ { "name": "string o null", "ingredients": [ { "name": "string", "quantity": number o null, "unit": "string o null" } ] } ],
  "stepGroups": [ { "name": "string o null", "instructions": ["string", ...] } ],
  "tags": ["string", ...],
  "utensils": ["string", ...]
}
Usa cantidades e ingredientes realistas para el número de raciones que elijas. Separa cada paso de
la elaboración como una instrucción independiente del array "instructions", en un orden lógico de
preparación. En "source" indica que la receta fue generada por IA (por ejemplo "Generada por IA").
Si no puedes determinar algún dato, usa null (o una lista vacía) en vez de inventarlo al azar.
""".trimIndent()
}
