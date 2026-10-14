package org.calamares.miga.data.vision

import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.KnownLabels
import org.calamares.miga.data.ai.knownLabelsInstruction
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
    /** Where the recipe comes from and its country code (see RecipeOrigin); null when not clear. */
    val origin: String? = null,
    val originCountry: String? = null,
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

/**
 * What a transcription from photos returns: every complete recipe found, in reading order, and
 * whether they belong together ([together]: parts of one dish or meant to be served together, such
 * as a cake and its frosting) or are independent recipes that happen to share a page.
 */
@Serializable
data class RecipeExtractionDto(
    val together: Boolean = false,
    val recipes: List<RecipeVisionResultDto> = emptyList()
)

sealed interface RecipeExtractionResult {
    /** [recipes] is never empty. */
    data class Success(val recipes: List<RecipeVisionResultDto>, val together: Boolean) : RecipeExtractionResult
    data class Error(val reason: String) : RecipeExtractionResult
}

sealed interface RecipeVisionResult {
    data class Success(val recipe: RecipeVisionResultDto) : RecipeVisionResult
    data class Error(val reason: String) : RecipeVisionResult
}

/**
 * Transcribes the recipes in one or more photos. Several images are treated as pages of the same
 * content; a page can hold more than one complete recipe, and each one is returned on its own.
 */
suspend fun AiCandidate.extractRecipes(images: List<AiImage>, known: KnownLabels = KnownLabels.NONE): RecipeExtractionResult {
    if (images.isEmpty()) return RecipeExtractionResult.Error(L10n.str(R.string.there_no_photos_process))
    return when (val result = complete(AiRequest(recipeExtractionPrompt(known), RECIPE_MAX_TOKENS, images))) {
        is AiText.Error -> RecipeExtractionResult.Error(result.reason)
        is AiText.Success -> parseExtractionAnswer(result.text)
    }
}

/**
 * Decodes the answer of [extractRecipes]. A model that ignores the list and answers with a single
 * recipe object is accepted too.
 */
internal fun parseExtractionAnswer(text: String): RecipeExtractionResult = try {
    val extraction = decodeAiJson(RecipeExtractionDto.serializer(), text)
    val recipes = extraction.recipes.ifEmpty {
        runCatching { listOf(decodeAiJson(RecipeVisionResultDto.serializer(), text)) }.getOrDefault(emptyList())
    }.filter { it.name.isNotBlank() }
    if (recipes.isEmpty()) {
        RecipeExtractionResult.Error(L10n.str(R.string.no_recipe_was_found_photo))
    } else {
        RecipeExtractionResult.Success(recipes, together = extraction.together && recipes.size > 1)
    }
} catch (e: Exception) {
    RecipeExtractionResult.Error(AiErrors.badResponse(e, text))
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
internal fun recipeExtractionPrompt(known: KnownLabels = KnownLabels.NONE): String = """
You are an assistant that transcribes cooking recipes from photos (of a cookbook, a magazine or a
handwritten recipe, sometimes with rotated text or columns).

Return ONLY a compact JSON object (no indentation or line breaks) with exactly this format, with no explanations or extra text:
{"together": true | false, "recipes": [ ${RECIPE_JSON_FORMAT.dropLast(2)},
  "dishPhotos": [ { "image": number, "box": [ymin, xmin, ymax, xmax], "rotation": 0 | 90 | 180 | 270 } ]
} ] }
"recipes" has one entry per COMPLETE recipe in the images (its own title, ingredients and steps), in
reading order; usually there is just one. Do not split one recipe into several: a part that belongs
to it (a sauce, a filling, a frosting described within it) stays inside it as a named ingredient
and step group. "together" is true when the recipes are parts of one dish or meant to be served
together (a cake and its frosting, a main course and its side), false when they are independent
recipes that happen to share a page; with a single recipe use false.
Put each preparation step as a separate entry of the "instructions" array, in the same order as in
the text. If you cannot determine a value, use null (or an empty list) instead of making it up. If
there is no recognisable recipe in the images, return an empty "recipes" list.
When several images are included, they are consecutive pages or fragments (for example photos of a
cookbook): a recipe that continues from one image to the next is ONE recipe; combine its parts
following the image order.
In each recipe's "dishPhotos" list where the photos of the FINISHED DISH are (the photo illustrating the recipe):
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
""".trimIndent() + knownLabelsInstruction(known) + transcriptionLanguageInstruction()
