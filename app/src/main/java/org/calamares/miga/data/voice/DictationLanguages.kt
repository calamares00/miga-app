package org.calamares.miga.data.voice

/** Idiomas ofrecidos para el dictado por voz (etiqueta BCP-47 + nombre en español). */
object DictationLanguages {

    /** Por defecto español de España: la app está en español y el idioma del móvil puede ser otro (p. ej. inglés). */
    const val DEFAULT = "es-ES"

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
