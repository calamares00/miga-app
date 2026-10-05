package org.calamares.miga.data.voice

import org.calamares.miga.L10n

/** Idiomas ofrecidos para el dictado por voz (etiqueta BCP-47 + nombre en español). */
object DictationLanguages {

    /**
     * Por defecto, el idioma de la app: español de España si la app está en español, inglés (de EE. UU.
     * si el móvil está en esa región, si no del Reino Unido) en cualquier otro caso. El usuario puede
     * elegir otro en Ajustes → Voz y dictado.
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
