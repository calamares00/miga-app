package com.bmo00.miga.data.model

private data class DurationPart(val start: Int, val end: Int, val seconds: Int)

/**
 * Busca una duración expresada en texto libre (p. ej. "cocer 10 minutos", "hornear 1 hora y 15
 * minutos") y la convierte a segundos, para ofrecer un botón de temporizador en el modo cocina.
 * Devuelve null si no encuentra ninguna duración reconocible.
 *
 * Si un paso menciona varias unidades muy seguidas ("1 hora y 30 minutos") se suman; si las
 * menciones están lejos entre sí (un paso largo con dos duraciones distintas para cosas distintas,
 * p. ej. "deja reposar 5 minutos... y hornea 40 minutos") solo se usa la primera - es la duración
 * con la que tiene sentido arrancar un temporizador al leer el paso.
 */
object StepTimerParsing {
    private val hoursRegex = Regex("""(\d+)\s*(?:horas?|h\.)\b""", RegexOption.IGNORE_CASE)
    private val minutesRegex = Regex("""(\d+)\s*(?:minutos?|mins?\.?)\b""", RegexOption.IGNORE_CASE)
    private val secondsRegex = Regex("""(\d+)\s*(?:segundos?|segs?\.?)\b""", RegexOption.IGNORE_CASE)

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
