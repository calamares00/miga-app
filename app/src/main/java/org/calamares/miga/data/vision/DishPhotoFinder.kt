package org.calamares.miga.data.vision

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.support.AppDispatchers
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiProgressReporter
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.data.local.CandidateCrop
import org.calamares.miga.data.local.DishPhotoCropper
import org.calamares.miga.data.local.NormalizedBox
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.local.boxWithinRegion
import org.calamares.miga.data.local.intersectionOverUnion
import org.calamares.miga.data.local.validDishBoxes
import org.calamares.miga.data.support.AiErrors

/** Enough for a few short entries, with room for a reasoning model to think first. */
private const val DISH_CHECK_MAX_TOKENS = 2048

/**
 * What the AI says about one candidate dish photo (image number [crop] of the request): whether
 * it really is a photo of the finished dish, whether it shows the [whole] dish rather than a
 * detail, the [box] to keep inside the candidate (`[ymin, xmin, ymax, xmax]`, 0 to 1000) and the
 * clockwise [rotation] that makes it upright.
 */
@Serializable
data class DishCheckDto(
    val crop: Int = -1,
    val dish: Boolean = false,
    val whole: Boolean = false,
    val box: List<Int> = emptyList(),
    val rotation: Int? = null
)

@Serializable
internal data class DishCheckAnswerDto(val photos: List<DishCheckDto> = emptyList())

sealed interface DishCheckResult {
    data class Success(val checks: List<DishCheckDto>) : DishCheckResult
    data class Error(val reason: String) : DishCheckResult
}

/**
 * Asks the model to look at each candidate on its own, enlarged. Locating a photo while
 * transcribing a whole page gives boxes that are often off, or on text; telling whether an image
 * is a dish and framing it inside a close-up is a much easier question, and any vision model
 * answers it well.
 */
suspend fun AiCandidate.checkDishPhotos(recipeName: String, candidates: List<AiImage>): DishCheckResult =
    when (val result = complete(AiRequest(dishCheckPrompt(recipeName, candidates.size), DISH_CHECK_MAX_TOKENS, candidates))) {
        is AiText.Error -> DishCheckResult.Error(result.reason)
        is AiText.Success -> try {
            DishCheckResult.Success(decodeAiJson(DishCheckAnswerDto.serializer(), result.text).photos)
        } catch (e: Exception) {
            DishCheckResult.Error(AiErrors.badResponse(e, result.text))
        }
    }

internal fun dishCheckPrompt(recipeName: String, count: Int): String = """
You check candidate photos for a cooking recipe called "${recipeName.replace('"', '\'')}".
The $count attached image(s) are the candidates, numbered from 0 in the order attached. Each one was
cut out, with some extra margin around it, from a photo of a cookbook or magazine page, a
handwritten recipe, a screenshot, or a direct photo of food. It may therefore include page text,
margins, other pictures, the binding, fingers or the table.

Return ONLY a compact JSON object, with no explanations:
{"photos":[{"crop":number,"dish":true|false,"whole":true|false,"box":[ymin,xmin,ymax,xmax],"rotation":0|90|180|270}]}
with exactly one entry per candidate.

"dish": true only if the candidate contains a real photograph of the FINISHED DISH of this recipe
(prepared food, ready to serve). It is false when what it shows is text, a title, an ingredient
list, a drawing, an icon or a logo, a person, loose or raw ingredients, an intermediate step of the
preparation, packaging, or clearly a different dish. If you are not sure it is the finished dish,
answer false.

"box": the part of THIS candidate image to keep, as [ymin, xmin, ymax, xmax] normalised from 0 to
1000 (0,0 is the top-left corner of the candidate).
- If the dish appears in a printed photograph on a page, the box is exactly that photograph, edge
  to edge: all of the photograph and nothing else. No text, title, caption, page number, page
  margin, frame, binding, fingers or table.
- If the candidate is a direct photo of the dish (no page around it), the box frames the dish with
  its plate or container, centred, with a small margin, leaving out what is not relevant (empty
  table, distant objects, people).
- The box must never contain text. When "dish" is false, use [].

"whole": true when the kept area shows the complete dish (the whole plate, pan, cake or serving);
false when it is a close-up detail, a slice or a partial view.

"rotation": degrees the kept area must be turned CLOCKWISE so the dish looks upright; 0 if it
already is.
""".trimIndent()

/**
 * The boxes to crop after the AI looked at the candidates: only those confirmed as the dish,
 * reframed with the box the AI gave inside each candidate, the ones showing the whole dish first
 * (the first becomes the recipe's cover). A candidate the AI did not mention is left out.
 */
internal fun confirmedDishBoxes(candidates: List<CandidateCrop>, checks: List<DishCheckDto>): List<NormalizedBox> {
    val confirmed = candidates.mapIndexedNotNull { index, candidate ->
        val check = checks.firstOrNull { it.crop == index && it.dish } ?: return@mapIndexedNotNull null
        val rotation = check.rotation ?: candidate.box.rotation
        val box = boxWithinRegion(candidate.region, check.box, rotation) ?: candidate.box.copy(rotation = rotation)
        box to check.whole
    }
    val kept = mutableListOf<NormalizedBox>()
    // Stable sort: among equals, the order the transcription gave them in.
    for ((box, _) in confirmed.sortedByDescending { it.second }) {
        if (kept.none { it.image == box.image && intersectionOverUnion(it, box) > 0.5f }) kept += box
    }
    return kept
}

/**
 * Finds, checks and saves the photos of the finished dish of [recipe] in the images it was read
 * from ([sources], in the order sent to the AI), and returns their uris with the main photo
 * first. The areas located during the transcription are only candidates: each is shown again to
 * the AI, enlarged, to confirm that it is the dish and to frame it. When that check cannot be
 * done (no provider answers), the candidates are used as they are.
 */
suspend fun SettingsRepository.findDishPhotos(
    context: Context,
    sources: List<Uri>,
    recipe: RecipeVisionResultDto,
    progress: AiProgressReporter? = null
): List<String> {
    val appContext = context.applicationContext
    val located = validDishBoxes(recipe.dishPhotos, sources.size)
    if (located.isEmpty()) return emptyList()
    progress?.step(L10n.str(R.string.ai_step_checking_dish_photo))
    val candidates = withContext(AppDispatchers.io) { DishPhotoCropper.candidateCrops(appContext, sources, located) }
    if (candidates.isEmpty()) return emptyList()
    val images = candidates.map { AiImage(it.jpeg, "image/jpeg") }
    val result = runAi<DishCheckResult>(
        needsImages = true,
        errorOf = { (it as? DishCheckResult.Error)?.reason },
        error = { DishCheckResult.Error(it) },
        progress = progress
    ) { ai -> ai.checkDishPhotos(recipe.name, images) }
    val boxes = when (result) {
        is DishCheckResult.Success -> confirmedDishBoxes(candidates, result.checks)
        else -> located
    }
    if (boxes.isEmpty()) return emptyList()
    progress?.step(L10n.str(R.string.ai_step_cropping_photo))
    return withContext(AppDispatchers.io) { DishPhotoCropper.extractBoxes(appContext, sources, boxes) }
}
