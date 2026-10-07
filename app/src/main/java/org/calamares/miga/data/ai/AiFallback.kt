package org.calamares.miga.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.support.ErrorDetail

private const val NETWORK_RETRY_DELAY_MILLIS = 3000L

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
 * @param progress receives which provider is being asked, retries and fallbacks.
 * @return the first non-provider-error result, or null when no provider is configured.
 */
suspend fun <T> SettingsRepository.runAi(
    needsImages: Boolean = false,
    errorOf: (T) -> String?,
    error: (String) -> T,
    progress: AiProgressReporter? = null,
    call: suspend (AiCandidate) -> T
): T? {
    val configured = aiCandidates()
    if (configured.isEmpty()) return null
    val usable = if (needsImages) configured.filter { supportsImages(it.provider) } else configured
    if (usable.isEmpty()) return error(L10n.str(R.string.ai_no_image_provider))

    val failures = mutableListOf<Pair<AiCandidate, String>>()
    AiKeepAlive.hold {
        for (candidate in usable) {
            val previous = failures.lastOrNull()?.first
            progress?.step(
                if (previous == null) L10n.str(R.string.ai_step_asking_x, candidate.provider.label)
                else L10n.str(R.string.ai_step_fallback_x_y, previous.provider.label, candidate.provider.label)
            )
            var result = call(candidate)
            var reason = errorOf(result) ?: return@hold result
            if (!ErrorDetail.isAiError(reason)) return@hold result
            // An unreadable answer (cut or malformed) is often a one-off, and a network error may
            // come from switching between Wi-Fi and mobile data: the same model gets a second
            // chance before moving on to the next provider.
            val summary = ErrorDetail.summary(reason)
            if (summary == L10n.str(R.string.ai_error_bad_response) || summary == L10n.str(R.string.ai_error_network)) {
                progress?.step(L10n.str(R.string.ai_step_retrying_x, candidate.provider.label))
                if (summary == L10n.str(R.string.ai_error_network)) delay(NETWORK_RETRY_DELAY_MILLIS)
                result = call(candidate)
                reason = errorOf(result) ?: return@hold result
                if (!ErrorDetail.isAiError(reason)) return@hold result
            }
            failures += candidate to reason
        }
        null
    }?.let { return it }
    if (failures.size == 1) return error(failures.single().second)
    val detail = failures.joinToString("\n\n") { (candidate, reason) ->
        "— ${candidate.provider.label} · ${candidate.model}\n${ErrorDetail.summary(reason)}" +
            (ErrorDetail.detail(reason)?.let { "\n$it" } ?: "")
    }
    val summary = L10n.str(R.string.ai_all_providers_failed, ErrorDetail.summary(failures.last().second))
    return error(ErrorDetail.markAsAi(ErrorDetail.withDetail(summary, detail)))
}
