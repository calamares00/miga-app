package org.calamares.miga.data.voice

import org.calamares.miga.L10n

/** Languages offered for voice dictation: BCP-47 tag and the language's own name. */
object DictationLanguages {

    /**
     * Defaults to the app language: Spanish (Spain) when the app is in Spanish, otherwise English
     * (US when the phone is set to that region, UK otherwise). Users can choose another one in
     * Settings > Voice and dictation.
     */
    val DEFAULT: String
        get() {
            val locale = L10n.locale()
            return when {
                locale.language == "es" -> "es-ES"
                locale.country == "US" -> "en-US"
                else -> "en-GB"
            }
        }

    val ALL: List<Pair<String, String>> = listOf(
        "es-ES" to "Español (España)",
        "es-MX" to "Español (México)",
        "es-AR" to "Español (Argentina)",
        "ca-ES" to "Català",
        "gl-ES" to "Galego",
        "eu-ES" to "Euskara",
        "en-GB" to "English (UK)",
        "en-US" to "English (US)",
        "fr-FR" to "Français",
        "pt-PT" to "Português",
        "it-IT" to "Italiano",
        "de-DE" to "Deutsch"
    )

    fun label(tag: String): String = ALL.firstOrNull { it.first == tag }?.second ?: tag
}
