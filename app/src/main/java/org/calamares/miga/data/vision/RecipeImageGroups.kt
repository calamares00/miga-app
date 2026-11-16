package org.calamares.miga.data.vision

import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.support.AiErrors

/** A short list of numbers, with room for a reasoning model to think first. */
private const val GROUPING_MAX_TOKENS = 2048

@Serializable
internal data class ImageGroupsDto(val groups: List<List<Int>> = emptyList())

sealed interface ImageGroupingResult {
    /** Every image index appears in exactly one of [groups]; never empty. */
    data class Success(val groups: List<List<Int>>) : ImageGroupingResult
    data class Error(val reason: String) : ImageGroupingResult
}

/**
 * Sorts the photos picked for an import into recipes: which ones are pages or parts of the same
 * recipe and which are different recipes. Each group is then transcribed on its own, so five
 * photos can be two pages of one recipe and three other recipes.
 */
suspend fun AiCandidate.groupRecipeImages(images: List<AiImage>): ImageGroupingResult {
    if (images.isEmpty()) return ImageGroupingResult.Error(L10n.str(R.string.there_no_photos_process))
    if (images.size == 1) return ImageGroupingResult.Success(listOf(listOf(0)))
    return when (val result = complete(AiRequest(imageGroupingPrompt(images.size), GROUPING_MAX_TOKENS, images))) {
        is AiText.Error -> ImageGroupingResult.Error(result.reason)
        is AiText.Success -> try {
            val groups = decodeAiJson(ImageGroupsDto.serializer(), result.text).groups
            ImageGroupingResult.Success(completeGroups(groups, images.size))
        } catch (e: Exception) {
            ImageGroupingResult.Error(AiErrors.badResponse(e, result.text))
        }
    }
}

internal fun imageGroupingPrompt(count: Int): String = """
The $count attached images, numbered from 0 in the order attached, are photos a person took to
save cooking recipes: pages of cookbooks or magazines, handwritten notes, screenshots and
sometimes a photo of the dish itself.

Group the images by recipe. Put images in the same group when:
- a recipe continues from one image to another (its title or ingredients in one, the steps in
  another; consecutive pages; the two halves of a page photographed separately);
- they are two photos of the same page or of the same recipe;
- one is a photo of the finished dish of the recipe in another.
Images of different recipes go in different groups. An image that holds several complete recipes
is one group on its own. When two consecutive images could be the same recipe and you are not
sure, keep them in the same group.

Return ONLY a compact JSON object, with no explanations: {"groups":[[0,1],[2],[3,4]]}
Every image number from 0 to ${count - 1} must appear exactly once. Inside a group, keep the reading
order of the recipe.
""".trimIndent()

/**
 * The groups the AI gave, made safe to use: numbers out of range or repeated are dropped, an
 * image the AI left out becomes a group of its own, and groups follow the order of their first
 * image.
 */
internal fun completeGroups(groups: List<List<Int>>, count: Int): List<List<Int>> {
    val seen = mutableSetOf<Int>()
    val cleaned = groups.map { group -> group.filter { it in 0 until count && seen.add(it) } }.filter { it.isNotEmpty() }
    val missing = (0 until count).filter { it !in seen }.map { listOf(it) }
    return (cleaned + missing).sortedBy { it.min() }
}
