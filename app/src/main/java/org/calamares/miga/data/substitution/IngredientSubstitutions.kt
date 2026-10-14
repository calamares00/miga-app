package org.calamares.miga.data.substitution

import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.ai.outputLanguageInstruction
import org.calamares.miga.data.support.AiErrors

private const val SUBSTITUTION_MAX_TOKENS = 1024

/** A suggested replacement for an ingredient and how to adjust the recipe (ratio, expected changes). */
data class IngredientSubstitution(val substitute: String, val notes: String)

sealed interface SubstitutionResult {
    data class Success(val substitutions: List<IngredientSubstitution>) : SubstitutionResult
    data class Error(val reason: String) : SubstitutionResult
}

@Serializable
internal data class IngredientSubstitutionDto(val substitute: String = "", val notes: String = "")

@Serializable
internal data class SubstitutionResultDto(val substitutions: List<IngredientSubstitutionDto> = emptyList())

/**
 * Suggests substitutes for [ingredientName] in the context of [recipeName] (replacing butter in a
 * sponge cake is not the same as in a sauce). Results are not cached.
 */
suspend fun AiCandidate.suggestSubstitutes(ingredientName: String, recipeName: String): SubstitutionResult =
    when (val result = complete(AiRequest(buildSubstitutionPrompt(ingredientName, recipeName), SUBSTITUTION_MAX_TOKENS))) {
        is AiText.Error -> SubstitutionResult.Error(result.reason)
        is AiText.Success -> try {
            val dto = decodeAiJson(SubstitutionResultDto.serializer(), result.text)
            val substitutions = dto.substitutions
                .filter { it.substitute.isNotBlank() }
                .map { IngredientSubstitution(it.substitute, it.notes) }
            if (substitutions.isEmpty()) {
                SubstitutionResult.Error(L10n.str(R.string.no_substitutes_found_ingredient))
            } else {
                SubstitutionResult.Success(substitutions)
            }
        } catch (e: Exception) {
            SubstitutionResult.Error(AiErrors.badResponse(e, result.text))
        }
    }

internal fun buildSubstitutionPrompt(ingredientName: String, recipeName: String): String = """
You are a cooking assistant. A user is making the recipe "$recipeName" and wants to replace the
ingredient "$ingredientName" (because they do not have it, cannot eat it or want an alternative).

Suggest between 2 and 4 reasonable substitutes for that ingredient IN THE CONTEXT of this specific
recipe (the same substitute may not work the same in a sponge cake as in a savoury sauce).

Return ONLY a compact JSON object (no indentation or line breaks) with exactly this format, with no explanations or extra text:
{
  "substitutions": [
    { "substitute": "string, name of the substitute", "notes": "string, 1 sentence: ratio and what changes (flavour, texture...)" }
  ]
}
Do not suggest the original ingredient as a substitute for itself.
""".trimIndent() + outputLanguageInstruction()
