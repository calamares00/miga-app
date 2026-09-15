package com.bmo00.miga.data.substitution

import com.bmo00.miga.data.vision.VisionProviderType
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
}

// Forma del JSON que se le pide al LLM; compartida entre proveedores para decodificar la respuesta.
@Serializable
internal data class IngredientSubstitutionDto(val substitute: String = "", val notes: String = "")

@Serializable
internal data class SubstitutionResultDto(val substitutions: List<IngredientSubstitutionDto> = emptyList())

// Prompt compartido entre todos los proveedores: deben pedir exactamente el mismo formato.
internal fun buildSubstitutionPrompt(ingredientName: String, recipeName: String): String = """
Eres un asistente de cocina. Un usuario está preparando la receta "$recipeName" y quiere
sustituir el ingrediente "$ingredientName" (porque no lo tiene, no puede tomarlo, o quiere una
alternativa).

Sugiere entre 2 y 4 sustitutos razonables para ese ingrediente EN EL CONTEXTO de esta receta
concreta (el mismo sustituto puede no valer igual en un bizcocho que en una salsa salada).

Devuelve ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional:
{
  "substitutions": [
    { "substitute": "string, nombre del sustituto", "notes": "string, 1 frase: proporción y qué cambia (sabor, textura...)" }
  ]
}
No sugieras el propio ingrediente original como sustituto de sí mismo.
""".trimIndent()
