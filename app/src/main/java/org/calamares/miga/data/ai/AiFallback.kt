package org.calamares.miga.data.ai

import kotlinx.coroutines.flow.first
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.support.ErrorDetail

/** A provider that is ready to use: it has both an API key and a model. */
data class AiCandidate(val provider: AiProvider, val apiKey: String, val model: String)

/** Configured providers, in the priority order chosen in Settings. */
suspend fun SettingsRepository.aiCandidates(): List<AiCandidate> =
    observeProviderOrder().first().mapNotNull { provider ->
        val key = apiKeyFor(provider)
        val model = modelFor(provider)
        if (key.isBlank() || model.isBlank()) null else AiCandidate(provider, key, model)
    }

/**
 * Runs an AI operation trying the configured providers in priority order.
 *
 * When a provider fails with a provider error (quota, key, network, unreadable answer...) the next
 * one is tried. Regular results such as "no recipe was found" are returned as they are, because
 * another model would most likely say the same.
 *
 * @param needsImages skips providers whose model cannot read images.
 * @param errorOf returns the error reason when [T] is an error, or null when it is a success.
 * @param error builds an error [T], used when no provider can read images and for the final
 *   summary when every provider failed.
 * @return the first non-provider-error result, or null when no provider is configured.
 */
suspend fun <T> SettingsRepository.runAi(
    needsImages: Boolean = false,
    errorOf: (T) -> String?,
    error: (String) -> T,
    call: suspend (AiCandidate) -> T
): T? {
    val configured = aiCandidates()
    if (configured.isEmpty()) return null
    val usable = if (needsImages) configured.filter { supportsImages(it.provider) } else configured
    if (usable.isEmpty()) return error(L10n.str(R.string.ai_no_image_provider))

    val failures = mutableListOf<Pair<AiCandidate, String>>()
    for (candidate in usable) {
        var result = call(candidate)
        var reason = errorOf(result) ?: return result
        if (!ErrorDetail.isAiError(reason)) return result
        // An answer that is not valid JSON is often a one-off (a cut or malformed answer), so the
        // same model gets a second chance before moving on to the next provider.
        if (ErrorDetail.summary(reason) == L10n.str(R.string.ai_error_bad_response)) {
            result = call(candidate)
            reason = errorOf(result) ?: return result
            if (!ErrorDetail.isAiError(reason)) return result
        }
        failures += candidate to reason
    }
    if (failures.size == 1) return error(failures.single().second)
    val detail = failures.joinToString("\n\n") { (candidate, reason) ->
        "— ${candidate.provider.label} · ${candidate.model}\n${ErrorDetail.summary(reason)}" +
            (ErrorDetail.detail(reason)?.let { "\n$it" } ?: "")
    }
    val summary = L10n.str(R.string.ai_all_providers_failed, ErrorDetail.summary(failures.last().second))
    return error(ErrorDetail.markAsAi(ErrorDetail.withDetail(summary, detail)))
}
