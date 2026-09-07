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

/** Una imagen (bytes ya en memoria + su mime type) para enviar a un cliente de visión. */
data class VisionImageInput(val bytes: ByteArray, val mimeType: String)

/** Reconoce y estructura el texto de una o varias fotos de una receta usando un LLM con visión.
 *  Cuando se pasa más de una imagen, se asume que todas son páginas/fragmentos de la misma receta. */
interface RecipeVisionClient {
    suspend fun extractRecipe(images: List<VisionImageInput>, apiKey: String, model: String): RecipeVisionResult
}

fun visionClientFor(provider: VisionProviderType): RecipeVisionClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiVisionClient
    VisionProviderType.ANTHROPIC -> AnthropicVisionClient
}

// Prompt compartido entre todos los proveedores de visión: deben pedir exactamente el mismo JSON,
// si no divergirían al cambiar de proveedor en Ajustes.
internal const val RECIPE_EXTRACTION_PROMPT = """
Eres un asistente que transcribe recetas de cocina a partir de una foto (de un libro, revista o
receta manuscrita, normalmente en español, a veces con el texto girado o en columnas). Devuelve
ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional:
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
Separa cada paso de la elaboración como una instrucción independiente del array "instructions", en
el mismo orden en que aparecen en el texto. Si no puedes determinar algún dato, usa null (o una
lista vacía) en vez de inventarlo. Si no reconoces ninguna receta en la imagen, deja "name" vacío.
Si se incluyen varias imágenes en esta petición, todas son páginas o fragmentos de la MISMA
receta (por ejemplo, fotos consecutivas de un libro de cocina); combina la información de todas
ellas en un único resultado, en el orden en que aparecen las imágenes.
"""

// Algunos proveedores envuelven el JSON en un bloque de markdown pese a pedir JSON puro; se lo
// quitamos antes de parsear. Compartida entre los clientes de Gemini y Anthropic (visión y salud).
internal fun stripMarkdownFences(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```")) return trimmed
    return trimmed
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}
