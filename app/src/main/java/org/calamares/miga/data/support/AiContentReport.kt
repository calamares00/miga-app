package org.calamares.miga.data.support

import org.calamares.miga.L10n
import org.calamares.miga.R
import java.net.URLEncoder

/** Reasons for reporting AI-generated content (Google Play AI-generated content policy). */
enum class AiReportReason(val label: String) {
    OFFENSIVE(L10n.str(R.string.offensive_inappropriate)),
    DANGEROUS(L10n.str(R.string.dangerous_food_allergies_health)),
    WRONG(L10n.str(R.string.wrong_nonsensical)),
    OTHER(L10n.str(R.string.other_reason))
}

/**
 * Builds a report about AI-generated content. Miga has no backend, so reports are sent to the
 * developer by email ([SUPPORT_EMAIL]).
 */
object AiContentReport {
    /** Support address for AI reports, "Report a problem" and the Play Store listing. */
    const val SUPPORT_EMAIL = "miga@calamares.org"
    private const val MAX_CONTENT_CHARS = 1500

    fun subject(feature: String): String = L10n.str(R.string.ai_content_report_x, feature)

    fun body(feature: String, reason: AiReportReason, comment: String, content: String, appVersion: String): String = buildString {
        appendLine(L10n.str(R.string.feature_x, feature))
        appendLine(L10n.str(R.string.report_reason_x, reason.label))
        if (comment.isNotBlank()) appendLine(L10n.str(R.string.report_comment_x, comment.trim()))
        appendLine(L10n.str(R.string.version_miga_x, appVersion))
        appendLine()
        appendLine(L10n.str(R.string.generated_content))
        append(content.trim().take(MAX_CONTENT_CHARS))
        if (content.trim().length > MAX_CONTENT_CHARS) append("…")
    }

    /** mailto link to the support address with the subject and body filled in. */
    fun targetUrl(subject: String, body: String): String =
        "mailto:$SUPPORT_EMAIL?subject=${encode(subject)}&body=${encode(body)}"

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
