package org.calamares.miga.data.vision

import org.calamares.miga.data.ai.OpenRouterVisionClient
import org.calamares.miga.L10n
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import kotlinx.serialization.Serializable

/**
 * Receta reconocida a partir de una foto. Es un DTO propio, independiente de [org.calamares.miga.
 * data.export.RecipeExportDto] (que tiene su propio versionado de esquema para export/import):
 * aquí no hace falta "uid" ni "recipeBookName" ni "photos", ese contexto lo aporta la pantalla
 * que recibe el resultado. Reutiliza [IngredientGroupDto]/[StepGroupDto] porque su forma es
 * exactamente la que necesita un LLM al describir ingredientes y pasos.
 */
@Serializable
data class RecipeVisionResultDto(
    val name: String,
    val categoryName: String? = null,
    val difficulty: String = "MEDIUM",
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int = 4,
    val notes: String = "",
    val source: String = "",
    val ingredientGroups: List<IngredientGroupDto> = emptyList(),
    val stepGroups: List<StepGroupDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val utensils: List<String> = emptyList(),
    /** Fotos del plato terminado encontradas en las imágenes (solo al importar desde fotos). */
    val dishPhotos: List<DishPhotoDto> = emptyList()
)

/**
 * Zona de una de las imágenes enviadas donde aparece una fotografía del plato: [image] es el
 * índice de la imagen (empezando en 0) y [box] es `[ymin, xmin, ymax, xmax]` normalizado de 0 a
 * 1000 (la convención nativa de Gemini para detección de objetos).
 */
@Serializable
data class DishPhotoDto(val image: Int = 0, val box: List<Int> = emptyList())

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
    VisionProviderType.OPENROUTER -> OpenRouterVisionClient
}

/** JSON format every recipe-producing prompt asks for; decoded into [RecipeVisionResultDto]. */
internal const val RECIPE_JSON_FORMAT = """{
  "name": "string",
  "categoryName": "string or null",
  "difficulty": "EASY" | "MEDIUM" | "HARD",
  "prepTimeMinutes": number or null,
  "cookTimeMinutes": number or null,
  "servings": number,
  "notes": "string",
  "source": "string",
  "ingredientGroups": [ { "name": "string or null", "ingredients": [ { "name": "string", "quantity": number or null, "unit": "string or null" } ] } ],
  "stepGroups": [ { "name": "string or null", "instructions": ["string", ...] } ],
  "tags": ["string", ...],
  "utensils": ["string", ...]
}"""

/** Prompt for transcribing a recipe from one or more photos; it also asks where the dish photos are. */
internal fun recipeExtractionPrompt(): String = """
You are an assistant that transcribes cooking recipes from photos (of a cookbook, a magazine or a
handwritten recipe, sometimes with rotated text or columns).

Return ONLY a JSON object with exactly this format, with no explanations or extra text:
${RECIPE_JSON_FORMAT.dropLast(2)},
  "dishPhotos": [ { "image": number, "box": [ymin, xmin, ymax, xmax] } ]
}
Put each preparation step as a separate entry of the "instructions" array, in the same order as in
the text. If you cannot determine a value, use null (or an empty list) instead of making it up. If
there is no recognisable recipe in the image, leave "name" empty.
When several images are included, they are all pages or fragments of the SAME recipe (for example
consecutive photos of a cookbook); combine them into a single result, following the image order.
In "dishPhotos" list where the photos of the FINISHED DISH are (the photo illustrating the recipe):
"image" is the zero-based image index and "box" is [ymin, xmin, ymax, xmax] normalised from 0 to
1000, fitted tightly inside the photo, without page margins, frames, borders, text, captions or
page numbers. If the whole image is a photo of the dish (no recipe text), use the box framing the
dish. Do not include photos of intermediate steps, loose ingredients, people or decorative
illustrations. At most 3; if there are none, leave the list empty.
""".trimIndent() + transcriptionLanguageInstruction()

private fun appLanguageName(): String = if (L10n.locale().language == "es") "Spanish" else "English"

/** Appended to prompts that GENERATE text so the AI answers in the app language. */
internal fun outputLanguageInstruction(): String =
    "\n\nWrite every text in the response (names, descriptions, steps, notes...) in ${appLanguageName()}."

/**
 * Appended to prompts that TRANSCRIBE an existing recipe (photo, web page): the recipe is stored in
 * the app language, translated if the original is in another language.
 */
internal fun transcriptionLanguageInstruction(): String {
    val language = appLanguageName()
    return "\n\nWrite the recipe in $language. If the original is in another language, translate all of it " +
        "(name, category, ingredients, units, steps, notes, tags and utensils) naturally; " +
        "if it is already in $language, transcribe it as is. Do not change quantities or the \"difficulty\" values."
}

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
