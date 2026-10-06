package org.calamares.miga.data.search

import kotlinx.serialization.Serializable
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.ai.outputLanguageInstruction
import org.calamares.miga.data.support.AiErrors

private const val SEARCH_MAX_TOKENS = 2048

/**
 * A dish suggested by the AI search. [origin] is the region it is typical of, or null when the
 * search was not geographic (for example "chicken curry recipes").
 */
data class DishSuggestion(val name: String, val description: String, val origin: String?)

sealed interface DishSearchResult {
    data class Success(val dishes: List<DishSuggestion>) : DishSearchResult
    data class Error(val reason: String) : DishSearchResult
}

@Serializable
internal data class DishSuggestionDto(val name: String = "", val description: String = "", val origin: String? = null)

@Serializable
internal data class DishSearchResultDto(val dishes: List<DishSuggestionDto> = emptyList())

/**
 * Suggests dishes for a free-form request (region, type of dish, ingredient...). The ideas come
 * from the model's own knowledge; it is not a web search.
 */
suspend fun AiCandidate.searchDishes(query: String): DishSearchResult =
    when (val result = complete(AiRequest(buildDishSearchPrompt(query), SEARCH_MAX_TOKENS))) {
        is AiText.Error -> DishSearchResult.Error(result.reason)
        is AiText.Success -> try {
            val dto = decodeAiJson(DishSearchResultDto.serializer(), result.text)
            DishSearchResult.Success(
                dto.dishes.filter { it.name.isNotBlank() }.map { DishSuggestion(it.name, it.description, it.origin) }
            )
        } catch (e: Exception) {
            DishSearchResult.Error(AiErrors.badResponse(e, result.text))
        }
    }

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
