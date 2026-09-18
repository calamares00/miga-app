package com.bmo00.miga.data.search

import com.bmo00.miga.data.vision.RecipeVisionResult
import com.bmo00.miga.data.vision.VisionProviderType

/**
 * Extrae una receta (mismo formato que el reconocimiento por foto, ver
 * [com.bmo00.miga.data.vision.RecipeVisionResultDto]) a partir del texto legible de una página web
 * ya descargada (ver [RecipeUrlFetcher]) - el LLM interpreta el texto, sin parseo estructurado de
 * microdatos (schema.org Recipe, JSON-LD...) de por medio.
 */
interface RecipeUrlImportClient {
    suspend fun importFromUrl(url: String, pageText: String, apiKey: String, model: String): RecipeVisionResult
}

fun recipeUrlImportClientFor(provider: VisionProviderType): RecipeUrlImportClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiRecipeUrlImportClient
    VisionProviderType.ANTHROPIC -> AnthropicRecipeUrlImportClient
}

// Prompt compartido entre todos los proveedores: pide exactamente el mismo JSON que ya usa
// RECIPE_EXTRACTION_PROMPT/buildDishRecipePrompt (mismo RecipeVisionResultDto), para no tener el
// formato duplicado en varios sitios con riesgo de que diverjan; solo cambia la instrucción de
// partida (interpretar el texto de una página en vez de una foto o generar desde cero).
internal fun buildUrlImportPrompt(url: String, pageText: String): String = """
Eres un asistente de cocina. A continuación tienes el texto extraído de una página web ($url) que
se supone que contiene una receta. El texto puede incluir ruido ajeno a la receta (menús de
navegación, publicidad, comentarios de otros usuarios, enlaces a otras recetas...); ignóralo y
quédate solo con la receta principal de la página.

Texto de la página:
---
$pageText
---

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
Si el texto no contiene ninguna receta reconocible, deja "name" vacío. En "source" pon la URL
original ($url). Si no puedes determinar algún dato, usa null (o una lista vacía) en vez de
inventarlo al azar.
""".trimIndent()
