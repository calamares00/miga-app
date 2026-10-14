package org.calamares.miga.data.model

private data class DurationPart(val start: Int, val end: Int, val seconds: Int)

/**
 * Finds a duration in free text ("cocer 10 minutos", "bake for 1 hour and 15 minutes") and converts
 * it to seconds so cook mode can offer a timer button. Returns null when no duration is found.
 *
 * Units mentioned close together ("1 hour and 30 minutes") are added up. When the mentions are far
 * apart (a long step with two durations for different things, such as "rest 5 minutes... then bake
 * 40 minutes") only the first one is used, since that is the timer that makes sense when reading
 * the step.
 */
object StepTimerParsing {
    private val hoursRegex = Regex("""(\d+)\s*(?:horas?|hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE)
    private val minutesRegex = Regex("""(\d+)\s*(?:minutos?|minutes?|mins?\.?)\b""", RegexOption.IGNORE_CASE)
    private val secondsRegex = Regex("""(\d+)\s*(?:segundos?|seconds?|secs?\.?)\b""", RegexOption.IGNORE_CASE)

    private const val MAX_GAP_TO_COMBINE = 15

    fun findTimerSeconds(text: String): Int? {
        val parts = listOfNotNull(
            hoursRegex.find(text)?.toDurationPart(3600),
            minutesRegex.find(text)?.toDurationPart(60),
            secondsRegex.find(text)?.toDurationPart(1)
        ).sortedBy { it.start }
        if (parts.isEmpty()) return null

        var total = parts.first().seconds
        var lastEnd = parts.first().end
        for (part in parts.drop(1)) {
            if (part.start - lastEnd > MAX_GAP_TO_COMBINE) break
            total += part.seconds
            lastEnd = part.end
        }
        return total.takeIf { it > 0 }
    }

    private fun MatchResult.toDurationPart(unitSeconds: Int): DurationPart {
        val value = groupValues[1].toIntOrNull() ?: 0
        return DurationPart(start = range.first, end = range.last, seconds = value * unitSeconds)
    }
}
