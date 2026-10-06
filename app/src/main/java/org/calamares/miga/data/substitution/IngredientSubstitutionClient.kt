package org.calamares.miga.data.substitution

import org.calamares.miga.data.ai.OpenRouterIngredientSubstitutionClient
import org.calamares.miga.data.vision.outputLanguageInstruction
import org.calamares.miga.data.vision.VisionProviderType
import kotlinx.serialization.Serializable

/** Un sustituto sugerido para un ingrediente: qué usar en su lugar y cómo ajustar la receta
 *  (proporción, cambios de sabor/textura esperables, etc.). */
data class IngredientSubstitution(val substitute: String, val notes: String)

sealed interface SubstitutionResult {
    data class Success(val substitutions: List<IngredientSubstitution>) : SubstitutionResult
    data class Error(val reason: String) : SubstitutionResult
}

/** Sugiere sustitutos para un ingrediente de una receta concreta (el contexto de la receta importa:
 *  no es lo mismo sustituir mantequilla en un bizcocho que en una salsa). No se cachea nada - es una
 *  consulta puntual, no un dato de la receta. */
interface IngredientSubstitutionClient {
    suspend fun suggestSubstitutes(ingredientName: String, recipeName: String, apiKey: String, model: String): SubstitutionResult
}

fun substitutionClientFor(provider: VisionProviderType): IngredientSubstitutionClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiIngredientSubstitutionClient
    VisionProviderType.ANTHROPIC -> AnthropicIngredientSubstitutionClient
    VisionProviderType.OPENROUTER -> OpenRouterIngredientSubstitutionClient
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class IngredientSubstitutionDto(val substitute: String = "", val notes: String = "")

@Serializable
internal data class SubstitutionResultDto(val substitutions: List<IngredientSubstitutionDto> = emptyList())

/** Prompt shared by every provider so the format never diverges. */
internal fun buildSubstitutionPrompt(ingredientName: String, recipeName: String): String = """
You are a cooking assistant. A user is making the recipe "$recipeName" and wants to replace the
ingredient "$ingredientName" (because they do not have it, cannot eat it or want an alternative).

Suggest between 2 and 4 reasonable substitutes for that ingredient IN THE CONTEXT of this specific
recipe (the same substitute may not work the same in a sponge cake as in a savoury sauce).

Return ONLY a JSON object with exactly this format, with no explanations or extra text:
{
  "substitutions": [
    { "substitute": "string, name of the substitute", "notes": "string, 1 sentence: ratio and what changes (flavour, texture...)" }
  ]
}
Do not suggest the original ingredient as a substitute for itself.
""".trimIndent() + outputLanguageInstruction()
