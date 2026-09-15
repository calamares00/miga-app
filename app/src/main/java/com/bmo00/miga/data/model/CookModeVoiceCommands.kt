package com.bmo00.miga.data.model

/** Comandos de voz reconocidos en el modo cocina (ver ui/detail/CookModeOverlay.kt). */
sealed interface CookVoiceCommand {
    data object NextStep : CookVoiceCommand
    data object PreviousStep : CookVoiceCommand
    data object RepeatStep : CookVoiceCommand
    data object StartTimer : CookVoiceCommand
    data object CancelTimer : CookVoiceCommand
}

/**
 * Interpreta el texto transcrito por [com.bmo00.miga.data.voice.SpeechDictation] como un comando
 * del modo cocina, por palabras clave (tolerante a variaciones de la transcripción, no exige una
 * frase exacta) en vez de por coincidencia exacta. Devuelve null si no reconoce ningún comando con
 * claridad, para no actuar sobre algo mal entendido.
 *
 * Las menciones al temporizador ("temporizador", "cuenta atrás") se resuelven aparte de
 * siguiente/anterior/repite: si el texto menciona el temporizador, un verbo de cancelación
 * (cancela, para, detén, quita...) decide si es para pararlo o para iniciarlo - así
 * "cancela el temporizador" y "pon el temporizador" no compiten por la misma palabra clave
 * "temporizador".
 */
object CookModeVoiceCommands {
    private val nextPhrases = listOf("siguiente", "adelante", "continua", "proximo paso")
    private val previousPhrases = listOf("anterior", "atras", "retrocede", "paso anterior")
    private val repeatPhrases = listOf("repite", "repetir", "otra vez", "de nuevo")
    private val cancelVerbs = listOf("cancela", "cancelar", "deten", "detener", "para", "quita", "quitar")
    private val timerWords = listOf("temporizador", "cuenta atras")

    fun parse(rawText: String): CookVoiceCommand? {
        val text = normalize(rawText)
        if (timerWords.any { text.contains(it) }) {
            return if (cancelVerbs.any { text.contains(it) }) CookVoiceCommand.CancelTimer else CookVoiceCommand.StartTimer
        }
        return when {
            nextPhrases.any { text.contains(it) } -> CookVoiceCommand.NextStep
            previousPhrases.any { text.contains(it) } -> CookVoiceCommand.PreviousStep
            repeatPhrases.any { text.contains(it) } -> CookVoiceCommand.RepeatStep
            else -> null
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
}
