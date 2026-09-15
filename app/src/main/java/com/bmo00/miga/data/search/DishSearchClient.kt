package com.bmo00.miga.data.search

import com.bmo00.miga.data.vision.VisionProviderType
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
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class DishSuggestionDto(val name: String = "", val description: String = "", val origin: String? = null)

@Serializable
internal data class DishSearchResultDto(val dishes: List<DishSuggestionDto> = emptyList())

// Prompt compartido entre todos los proveedores: deben pedir exactamente el mismo formato, si no
// divergirían al cambiar de proveedor en Ajustes.
internal fun buildDishSearchPrompt(query: String): String = """
Eres un asistente experto en cocina y gastronomía de todo el mundo. Un usuario de una app de
recetas busca ideas con esta petición: "$query" (puede ser una zona/país, un tipo de plato, un
ingrediente principal, una ocasión, o cualquier descripción libre en español).

Sugiere entre 6 y 10 platos que encajen bien con la petición. Si la petición es geográfica
(zona/país/región), prioriza platos realmente típicos y reconocibles de ese lugar; si no lo es,
propón ideas de recetas variadas que encajen con lo pedido.

Devuelve ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional:
{
  "dishes": [
    { "name": "string", "description": "string, 1-2 frases", "origin": "string o null" }
  ]
}
"origin" es la zona/país/región de la que es típico el plato, o null si no aplica. No repitas
platos y no dejes ningún "name" vacío.
""".trimIndent()
