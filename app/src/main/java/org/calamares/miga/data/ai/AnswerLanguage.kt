package org.calamares.miga.data.ai

/**
 * Guardrail for recipes written by the AI (imports from photos or web pages, recipes of the AI
 * search): they must come in the app language. The prompt asks for it, but a model sometimes
 * answers in English anyway, so the answer is checked and, when it is clearly in the other
 * language, asked again once with a correction. If the second answer is still wrong it is used
 * as it is: a recipe in the wrong language is better than none.
 */
internal suspend fun AiCandidate.completeInAppLanguage(request: AiRequest): AiText {
    val target = appLanguageCode()
    val first = complete(request)
    if (first !is AiText.Success) return first
    val found = AnswerLanguage.detect(first.text)
    if (found == null || found == target) return first
    val second = complete(request.copy(prompt = request.prompt + languageCorrectionInstruction(found)))
    return if (second is AiText.Success) second else first
}

/** Tells Spanish from English in a JSON answer by its most common words. */
internal object AnswerLanguage {

    private val SPANISH = setOf(
        "de", "la", "el", "los", "las", "y", "con", "en", "para", "una", "del", "al", "hasta", "se", "que",
        "por", "sin", "lo", "su", "unos", "unas", "durante", "minutos", "muy", "bien", "añadir", "añade", "mezclar"
    )
    private val ENGLISH = setOf(
        "the", "and", "with", "of", "to", "in", "for", "until", "into", "or", "on", "about", "minutes", "it",
        "add", "mix", "over", "then", "from", "is", "be", "your"
    )

    /** JSON keys ("name": ...) are English whatever the answer's language, so they are not counted. */
    private val KEY = Regex("\"[A-Za-z]+\"\\s*:")
    private val WORD = Regex("\\p{L}+")

    private const val MIN_HITS = 6

    /** "es", "en", or null when there is too little text or no clear winner. */
    fun detect(text: String): String? {
        var spanish = 0
        var english = 0
        WORD.findAll(KEY.replace(text, " ")).forEach {
            val word = it.value.lowercase()
            if (word in SPANISH) spanish++
            if (word in ENGLISH) english++
        }
        return when {
            spanish + english < MIN_HITS -> null
            spanish >= english * 2 -> "es"
            english >= spanish * 2 -> "en"
            else -> null
        }
    }
}
