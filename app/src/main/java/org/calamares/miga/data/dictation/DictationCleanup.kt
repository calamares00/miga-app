package org.calamares.miga.data.dictation

import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete

private const val CLEANUP_MAX_TOKENS = 512

sealed interface DictationCleanupResult {
    data class Success(val text: String) : DictationCleanupResult
    data class Error(val reason: String) : DictationCleanupResult
}

/**
 * Polishes a recipe step dictated by voice (filler words, punctuation, self-corrections). Speech
 * recognition itself runs on the device (see SpeechDictation); this only cleans up its output, as
 * plain text rather than JSON since a structured format would only add a way to fail.
 */
suspend fun AiCandidate.cleanUpDictation(rawText: String): DictationCleanupResult =
    when (val result = complete(AiRequest(buildDictationCleanupPrompt(rawText), CLEANUP_MAX_TOKENS, expectJson = false))) {
        is AiText.Error -> DictationCleanupResult.Error(result.reason)
        is AiText.Success -> DictationCleanupResult.Success(stripSurroundingQuotes(result.text))
    }

internal fun buildDictationCleanupPrompt(rawText: String): String = """
    The following step of a cooking recipe was dictated by voice, so it may contain filler words,
    repetitions, missing punctuation or a correction halfway through a sentence (for example
    "no wait, better put..."). Rewrite it as a single clear recipe instruction in the same language
    it was dictated in (do not translate it), keeping the meaning and the order of what was said.
    Do not add information that is not in the original text and do not change quantities or
    ingredients. Return only the final text of the step, without quotes or explanations.

    Dictated text: "$rawText"
""".trimIndent()

/** Removes the surrounding quotes that models sometimes add even when asked not to. */
internal fun stripSurroundingQuotes(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
        trimmed.substring(1, trimmed.length - 1).trim()
    } else {
        trimmed
    }
}
