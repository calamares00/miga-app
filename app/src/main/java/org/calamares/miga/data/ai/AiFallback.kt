package org.calamares.miga.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.support.ErrorDetail

private const val NETWORK_RETRY_DELAY_MILLIS = 3000L

// A provider at its rate limit is waited for when the wait it asks for is short, at most this many times.
private const val MAX_RATE_LIMIT_WAIT_MILLIS = 90_000L
private const val MAX_RATE_LIMIT_RETRIES = 2
private const val RATE_LIMIT_MARGIN_MILLIS = 1000L

/**
 * A provider that is ready to use: it has an API key and a model (a custom server needs its
 * [baseUrl] instead of a key).
 */
data class AiCandidate(val provider: AiProvider, val apiKey: String, val model: String, val baseUrl: String? = null)

/** Configured providers, in the priority order chosen in Settings, without the ones switched off. */
suspend fun SettingsRepository.aiCandidates(): List<AiCandidate> {
    val disabled = observeDisabledProviders().first()
    return observeProviderOrder().first().filter { it !in disabled }.mapNotNull { provider ->
        val key = apiKeyFor(provider)
        val model = modelFor(provider)
        val baseUrl = if (provider == AiProvider.CUSTOM) observeCustomBaseUrl().first() else null
        val ready = model.isNotBlank() && if (provider == AiProvider.CUSTOM) !baseUrl.isNullOrBlank() else key.isNotBlank()
        if (ready) AiCandidate(provider, key, model, baseUrl) else null
    }
}

/**
 * Runs an AI operation trying the configured providers in priority order.
 *
 * When a provider fails with a provider error (quota, key, network, unreadable answer...) the next
 * one is tried. Regular results such as "no recipe was found" are returned as they are, because
 * another model would most likely say the same.
 *
 * @param needsImages skips providers whose model cannot read images.
 * @param accepts skips the providers it rejects (those that cannot take audio, say).
 * @param errorOf returns the error reason when [T] is an error, or null when it is a success.
 * @param error builds an error [T], used when no provider can read images and for the final
 *   summary when every provider failed.
 * @param progress receives which provider is being asked, retries and fallbacks.
 * @return the first non-provider-error result, or null when no provider is configured.
 */
suspend fun <T> SettingsRepository.runAi(
    needsImages: Boolean = false,
    accepts: (AiCandidate) -> Boolean = { true },
    errorOf: (T) -> String?,
    error: (String) -> T,
    progress: AiProgressReporter? = null,
    call: suspend (AiCandidate) -> T
): T? {
    val configured = aiCandidates().filter(accepts)
    if (configured.isEmpty()) return null
    val usable = if (needsImages) configured.filter { supportsImages(it.provider) } else configured
    if (usable.isEmpty()) return error(L10n.str(R.string.ai_no_image_provider))

    val failures = mutableListOf<Pair<AiCandidate, String>>()
    AiKeepAlive.hold {
        for ((position, candidate) in usable.withIndex()) {
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
            // At its rate limit with a short wait (Groq's free tier: tokens per minute): the last
            // provider left is waited for and asked again; with others still to try, moving on is faster.
            var rateLimitRetries = 0
            while (position == usable.lastIndex && rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                val wait = RateLimits.waitMillis(reason)?.takeIf { it <= MAX_RATE_LIMIT_WAIT_MILLIS } ?: break
                rateLimitRetries++
                waitForRateLimit(candidate, wait + RATE_LIMIT_MARGIN_MILLIS, progress)
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

/** Waits [millis] while the progress line counts down the seconds left. */
private suspend fun waitForRateLimit(candidate: AiCandidate, millis: Long, progress: AiProgressReporter?) {
    val end = System.currentTimeMillis() + millis
    progress?.step(L10n.str(R.string.ai_step_rate_limited_x, candidate.provider.label))
    while (true) {
        val left = end - System.currentTimeMillis()
        if (left <= 0) break
        progress?.detail(L10n.str(R.string.ai_retrying_in_x_seconds, ((left + 999) / 1000).toInt()))
        delay(minOf(left, 1000L))
    }
    progress?.step(L10n.str(R.string.ai_step_retrying_x, candidate.provider.label))
}
