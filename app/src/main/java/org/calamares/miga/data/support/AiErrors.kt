package org.calamares.miga.data.support

import org.calamares.miga.L10n
import org.calamares.miga.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Error messages with two parts: a short summary for the user and an optional technical detail
 * (model answer, HTTP code...) that is only shown under "Show details". Both parts travel in the
 * same String, separated by [SEPARATOR], so result types stay plain strings.
 */
object ErrorDetail {
    private const val SEPARATOR = "\u001F"
    /**
     * Leading mark: the error comes from an AI provider, so offering a different model makes sense.
     */
    private const val AI_MARK = "\u001E"

    fun withDetail(summary: String, detail: String?): String =
        if (detail.isNullOrBlank()) summary else summary + SEPARATOR + detail

    fun markAsAi(reason: String): String = if (reason.startsWith(AI_MARK)) reason else AI_MARK + reason

    fun isAiError(reason: String): Boolean = reason.startsWith(AI_MARK)

    fun summary(reason: String): String = reason.removePrefix(AI_MARK).substringBefore(SEPARATOR)

    fun detail(reason: String): String? = reason.substringAfter(SEPARATOR, "").takeIf { it.isNotBlank() }
}

/** AI provider errors turned into readable messages. */
object AiErrors {

    /** The AI answered, but not with the expected JSON. */
    fun badResponse(error: Throwable, modelText: String): String {
        val technical = error.message?.substringBefore("\nJSON input:") ?: error::class.simpleName.orEmpty()
        return ErrorDetail.markAsAi(ErrorDetail.withDetail(L10n.str(R.string.ai_error_bad_response), "$technical\n\n$modelText"))
    }

    /** Unsuccessful HTTP response from [provider] (Gemini, Claude...). */
    fun http(provider: String, code: Int, providerMessage: String?): String {
        val summary = when (code) {
            400 -> L10n.str(R.string.ai_error_bad_request, provider)
            401, 403 -> L10n.str(R.string.ai_error_key, provider)
            404 -> L10n.str(R.string.ai_error_model, provider)
            402, 429 -> L10n.str(R.string.ai_error_quota, provider)
            in 500..599 -> L10n.str(R.string.ai_error_unavailable, provider)
            else -> L10n.str(R.string.ai_error_generic, provider)
        }
        return ErrorDetail.markAsAi(ErrorDetail.withDetail(summary, "HTTP $code" + (providerMessage?.let { "\n$it" } ?: "")))
    }

    /** Network or unexpected failure while calling the AI. */
    fun exception(error: Throwable): String {
        val summary = when (error) {
            is UnknownHostException, is SocketTimeoutException -> L10n.str(R.string.ai_error_network)
            is IOException -> L10n.str(R.string.ai_error_network)
            else -> L10n.str(R.string.ai_error_unexpected)
        }
        return ErrorDetail.markAsAi(ErrorDetail.withDetail(summary, error.toString()))
    }
}
