package org.calamares.miga.data.search

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiAudio
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiProgressReporter
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.AudioSupport
import org.calamares.miga.data.ai.KnownLabels
import org.calamares.miga.data.ai.RECIPE_JSON_FORMAT
import org.calamares.miga.data.ai.audioSupport
import org.calamares.miga.data.ai.completeInAppLanguage
import org.calamares.miga.data.ai.knownLabelsInstruction
import org.calamares.miga.data.ai.transcribe
import org.calamares.miga.data.ai.transcriptionLanguageInstruction
import org.calamares.miga.data.vision.RECIPE_MAX_TOKENS
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.parseRecipeAnswer

/**
 * The recipe told in a post's video: Gemini listens to [audio] itself; providers with a
 * speech-to-text service transcribe it first and their chat model reads the text. Either way the
 * model also gets the post's caption. Providers with no [audioSupport] are not to be asked.
 */
suspend fun AiCandidate.importRecipeFromPostAudio(
    post: SocialPost,
    audio: PostAudio,
    known: KnownLabels = KnownLabels.NONE,
    progress: AiProgressReporter? = null
): RecipeVisionResult {
    val noRecipe = { L10n.str(R.string.no_recipe_in_post_video) }
    return when (audioSupport) {
        AudioSupport.LISTENS -> {
            val aac = audio.aac ?: return RecipeVisionResult.Error(noRecipe())
            val request = AiRequest(buildPostAudioPrompt(post, transcript = null, known), RECIPE_MAX_TOKENS, audio = AiAudio(aac, "audio/aac"))
            parseRecipeAnswer(completeInAppLanguage(request), noRecipe)
        }
        AudioSupport.TRANSCRIBES -> {
            progress?.step(L10n.str(R.string.ai_step_transcribing_x, provider.label))
            when (val transcript = transcribe(audio.m4a)) {
                is AiText.Error -> RecipeVisionResult.Error(transcript.reason)
                is AiText.Success -> if (transcript.text.isBlank()) {
                    RecipeVisionResult.Error(noRecipe())
                } else {
                    progress?.step(L10n.str(R.string.ai_step_asking_x, provider.label))
                    val request = AiRequest(buildPostAudioPrompt(post, transcript.text, known), RECIPE_MAX_TOKENS)
                    parseRecipeAnswer(completeInAppLanguage(request), noRecipe)
                }
            }
        }
        null -> RecipeVisionResult.Error(noRecipe())
    }
}

/**
 * Prompt for a recipe told in a video: with the [transcript] of its audio, or (null) with the
 * audio attached for a model that listens.
 */
internal fun buildPostAudioPrompt(post: SocialPost, transcript: String?, known: KnownLabels = KnownLabels.NONE): String {
    val audioPart = if (transcript != null) {
        "Below are the caption of a cooking video posted on ${post.site} (${post.url}) and the transcript of what is said in it.\n\n" +
            "Transcript of the video:\n---\n$transcript\n---"
    } else {
        "Below is the caption of a cooking video posted on ${post.site} (${post.url}); the audio of the video is attached. " +
            "Listen to it: the recipe is usually told aloud (ingredients, quantities and steps)."
    }
    return """
You are a cooking assistant. $audioPart

Caption of the post${post.author?.let { " by $it" }.orEmpty()}:
---
${post.text}
---

Write the recipe that is cooked in the video, joining what is said with what the caption says.
Use only quantities that are said or written; when one is not given, leave "quantity" null rather
than guessing. Ignore greetings, promotions and requests to follow, like or comment.

Return ONLY a compact JSON object (no indentation or line breaks) with exactly this format, with no explanations or extra text:
$RECIPE_JSON_FORMAT
If neither the video nor the caption contains a recipe, leave "name" empty. Put the post's URL
(${post.url}) in "source". If you cannot determine a value, use null (or an empty list) instead of making it up.
""".trimIndent() + knownLabelsInstruction(known) + transcriptionLanguageInstruction()
}
