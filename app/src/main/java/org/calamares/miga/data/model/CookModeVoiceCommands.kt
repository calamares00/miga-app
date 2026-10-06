package org.calamares.miga.data.model

/** Voice commands understood in cooking mode (see ui/detail/CookModeOverlay.kt). */
sealed interface CookVoiceCommand {
    data object NextStep : CookVoiceCommand
    data object PreviousStep : CookVoiceCommand
    data object RepeatStep : CookVoiceCommand
    data object StartTimer : CookVoiceCommand
    data object CancelTimer : CookVoiceCommand
}

/**
 * Turns the text transcribed by [org.calamares.miga.data.voice.SpeechDictation] into a cooking mode
 * command by keywords, tolerating variations in the transcription. Returns null when no command is
 * recognised clearly, so nothing happens on a misheard phrase.
 *
 * Timer mentions are resolved first: a cancel verb said before the timer word ("stop the timer",
 * "para el temporizador") cancels it, anything else starts it ("pon el temporizador para la
 * pasta").
 */
object CookModeVoiceCommands {
    /** Spanish and English, since dictation can use either language (Settings > Voice). */
    private val nextPhrases = listOf("siguiente", "adelante", "continua", "proximo paso", "next", "forward")
    private val previousPhrases = listOf("anterior", "atras", "retrocede", "paso anterior", "previous", "back")
    private val repeatPhrases = listOf("repite", "repetir", "otra vez", "de nuevo", "repeat", "again")
    private val cancelVerbs = listOf("cancela", "cancelar", "deten", "detener", "para", "quita", "quitar", "cancel", "stop")
    private val timerWords = listOf("temporizador", "cuenta atras", "timer", "countdown")

    fun parse(rawText: String): CookVoiceCommand? {
        val text = normalize(rawText)
        val timerAt = timerWords.mapNotNull { indexOfPhrase(text, it) }.minOrNull()
        if (timerAt != null) {
            val cancelBeforeTimer = cancelVerbs.any { verb -> indexOfPhrase(text, verb)?.let { it < timerAt } == true }
            return if (cancelBeforeTimer) CookVoiceCommand.CancelTimer else CookVoiceCommand.StartTimer
        }
        return when {
            nextPhrases.any { indexOfPhrase(text, it) != null } -> CookVoiceCommand.NextStep
            previousPhrases.any { indexOfPhrase(text, it) != null } -> CookVoiceCommand.PreviousStep
            repeatPhrases.any { indexOfPhrase(text, it) != null } -> CookVoiceCommand.RepeatStep
            else -> null
        }
    }

    /** Position of [phrase] as whole words in [text] ("para" does not match "preparar"), or null. */
    private fun indexOfPhrase(text: String, phrase: String): Int? =
        Regex("(^|[^\\p{L}])" + Regex.escape(phrase) + "($|[^\\p{L}])").find(text)?.range?.first

    private fun normalize(text: String): String =
        text.lowercase().replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
}
