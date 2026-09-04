package com.bmo00.miga.data.vision

/**
 * Modelos de Gemini con soporte de visión que se ofrecen en el desplegable de Ajustes. Google
 * va renovando este catálogo con el tiempo (el anterior, "gemini-2.0-flash", ya no existe), así
 * que además de esta lista curada, la pantalla de ajustes deja escribir cualquier otro id a mano
 * ("Personalizado") sin necesitar una actualización de la app.
 */
val GEMINI_MODELS = listOf(
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite"
)

const val DEFAULT_GEMINI_MODEL = "gemini-3.6-flash"
