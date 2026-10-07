package org.calamares.miga.data.vision

import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.RECIPE_JSON_FORMAT
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.ai.transcriptionLanguageInstruction
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import org.calamares.miga.data.support.AiErrors

/** Output budget for a full recipe; long recipes with many steps need several thousand tokens. */
internal const val RECIPE_MAX_TOKENS = 8192

/**
 * A recipe produced by an AI model (from photos, a web page or generated from a dish name).
 *
 * Independent from the backup format (RecipeExportDto) on purpose: it has no uid, book or photos,
 * which the screen receiving the result provides. Ingredient and step groups reuse the backup DTOs
 * because their shape is exactly what a model needs to describe them.
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
    /** Photos of the finished dish found in the images (only when importing from photos). */
    val dishPhotos: List<DishPhotoDto> = emptyList()
)

/**
 * Area of one of the sent images that contains a photo of the dish. [image] is the zero-based
 * image index and [box] is `[ymin, xmin, ymax, xmax]` normalised from 0 to 1000, Gemini's native
 * convention for object detection. [rotation] is the clockwise turn, in degrees, that makes the
 * cropped photo upright when the page was photographed sideways or upside down.
 */
@Serializable
data class DishPhotoDto(val image: Int = 0, val box: List<Int> = emptyList(), val rotation: Int = 0)

sealed interface RecipeVisionResult {
    data class Success(val recipe: RecipeVisionResultDto) : RecipeVisionResult
    data class Error(val reason: String) : RecipeVisionResult
}

/**
 * Transcribes a recipe from one or more photos. Several images are treated as pages of the same
 * recipe.
 */
suspend fun AiCandidate.extractRecipe(images: List<AiImage>): RecipeVisionResult {
    if (images.isEmpty()) return RecipeVisionResult.Error(L10n.str(R.string.there_no_photos_process))
    val result = complete(AiRequest(recipeExtractionPrompt(), RECIPE_MAX_TOKENS, images))
    return parseRecipeAnswer(result) { L10n.str(R.string.no_recipe_was_found_photo) }
}

/** Decodes a recipe answer; [emptyMessage] is used when the model found no recipe at all. */
internal fun parseRecipeAnswer(result: AiText, emptyMessage: () -> String): RecipeVisionResult = when (result) {
    is AiText.Error -> RecipeVisionResult.Error(result.reason)
    is AiText.Success -> try {
        val recipe = decodeAiJson(RecipeVisionResultDto.serializer(), result.text)
        if (recipe.name.isBlank()) RecipeVisionResult.Error(emptyMessage()) else RecipeVisionResult.Success(recipe)
    } catch (e: Exception) {
        RecipeVisionResult.Error(AiErrors.badResponse(e, result.text))
    }
}

/** Transcription prompt; it also asks where the photos of the finished dish are. */
internal fun recipeExtractionPrompt(): String = """
You are an assistant that transcribes cooking recipes from photos (of a cookbook, a magazine or a
handwritten recipe, sometimes with rotated text or columns).

Return ONLY a compact JSON object (no indentation or line breaks) with exactly this format, with no explanations or extra text:
${RECIPE_JSON_FORMAT.dropLast(2)},
  "dishPhotos": [ { "image": number, "box": [ymin, xmin, ymax, xmax], "rotation": 0 | 90 | 180 | 270 } ]
}
Put each preparation step as a separate entry of the "instructions" array, in the same order as in
the text. If you cannot determine a value, use null (or an empty list) instead of making it up. If
there is no recognisable recipe in the image, leave "name" empty.
When several images are included, they are all pages or fragments of the SAME recipe (for example
consecutive photos of a cookbook); combine them into a single result, following the image order.
In "dishPhotos" list where the photos of the FINISHED DISH are (the photo illustrating the recipe):
"image" is the zero-based image index and "box" is [ymin, xmin, ymax, xmax] normalised from 0 to
1000 (0,0 is the top-left corner of the image). Measure the box on the edges of the printed
photograph itself, as precisely as possible: it must contain only the photograph, with no
surrounding page, text, titles, captions, page numbers, margins, frames, ring or spiral binding,
fingers or the table under the book. If an edge is uncertain, keep it slightly inside the photo.
If the whole image is a photo of the dish (no recipe text), use the box framing the dish. Do not
include photos of intermediate steps, loose ingredients, people or decorative illustrations. At
most 3; if there are none, leave the list empty.
"rotation" is how many degrees the cropped photo must be turned CLOCKWISE to look upright (the
dish the right way up, as printed on the page). Use the page text as a guide: if the text in the
image reads sideways or upside down, the photo is rotated the same way. Use 0 when it is already
upright.
""".trimIndent() + transcriptionLanguageInstruction()
