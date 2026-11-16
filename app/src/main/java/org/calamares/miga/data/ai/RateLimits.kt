package org.calamares.miga.data.ai

import org.calamares.miga.data.support.ErrorDetail

/**
 * How long a provider asks to wait after a rate limit error (HTTP 429), read from the error built
 * by AiErrors.http: the Retry-After header, or the wait the message states. Groq ("Please try
 * again in 51.96s"), OpenAI ("try again in 1m20s", "in 450ms") and Gemini ("Please retry in
 * 31.2s") say it in the text.
 */
internal object RateLimits {

    /** Milliseconds to wait before repeating the request, or null when it is not a rate limit or no wait is given. */
    fun waitMillis(reason: String): Long? {
        val detail = ErrorDetail.detail(reason) ?: return null
        if (!detail.startsWith("HTTP 429")) return null
        RETRY_AFTER.find(detail)?.let { return (it.groupValues[1].toDouble() * 1000).toLong() }
        DURATION.find(detail)?.let { match ->
            val minutes = match.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
            val amount = match.groupValues[2].toDouble()
            val millis = if (match.groupValues[3] == "ms") amount else amount * 1000
            return (minutes * 60_000 + millis).toLong()
        }
        SECONDS_WORD.find(detail)?.let { return (it.groupValues[1].toDouble() * 1000).toLong() }
        return null
    }

    private val RETRY_AFTER = Regex("""Retry-After:\s*(\d+(?:\.\d+)?)\s*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val DURATION = Regex("""(?:try again|retry) (?:in|after) (?:(\d+)m)?(\d+(?:\.\d+)?)(ms|s)\b""", RegexOption.IGNORE_CASE)
    private val SECONDS_WORD = Regex("""(?:try again|retry) (?:in|after) (\d+(?:\.\d+)?) ?seconds?""", RegexOption.IGNORE_CASE)
}
