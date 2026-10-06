package org.calamares.miga.data.search

import org.calamares.miga.data.ai.OpenRouterDishSearchClient
import org.calamares.miga.data.vision.outputLanguageInstruction
import org.calamares.miga.data.vision.VisionProviderType
import kotlinx.serialization.Serializable

/** Un plato sugerido por la búsqueda con IA: nombre, breve descripción, y origen/región si aplica
 *  (null si la búsqueda no era geográfica, p. ej. "recetas con pollo y curry"). */
data class DishSuggestion(val name: String, val description: String, val origin: String?)

sealed interface DishSearchResult {
    data class Success(val dishes: List<DishSuggestion>) : DishSearchResult
    data class Error(val reason: String) : DishSearchResult
}

/** Busca ideas de platos a partir de una petición libre (zona/país, tipo de plato, ingrediente, o
 *  cualquier descripción) usando un LLM - son sugerencias generadas por el propio modelo a partir
 *  de su conocimiento, no una búsqueda real en la web. */
interface DishSearchClient {
    suspend fun searchDishes(query: String, apiKey: String, model: String): DishSearchResult
}

fun dishSearchClientFor(provider: VisionProviderType): DishSearchClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiDishSearchClient
    VisionProviderType.ANTHROPIC -> AnthropicDishSearchClient
    VisionProviderType.OPENROUTER -> OpenRouterDishSearchClient
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class DishSuggestionDto(val name: String = "", val description: String = "", val origin: String? = null)

@Serializable
internal data class DishSearchResultDto(val dishes: List<DishSuggestionDto> = emptyList())

/** Prompt shared by every provider so the format never diverges. */
internal fun buildDishSearchPrompt(query: String): String = """
You are an expert in cooking and cuisines from all over the world. A user of a recipe app is
looking for ideas with this request: "$query" (it may be a region or country, a type of dish, a
main ingredient, an occasion or any free description, in any language).

Suggest between 6 and 10 dishes that fit the request well. If the request is geographic (area,
country or region), favour dishes that are truly typical and recognisable from that place;
otherwise, suggest a varied set of recipe ideas that match the request.

Return ONLY a JSON object with exactly this format, with no explanations or extra text:
{
  "dishes": [
    { "name": "string", "description": "string, 1-2 sentences", "origin": "string or null" }
  ]
}
"origin" is the area, country or region the dish is typical of, or null if it does not apply.
Do not repeat dishes and do not leave any "name" empty.
""".trimIndent() + outputLanguageInstruction()
