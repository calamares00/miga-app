package com.bmo00.miga.data.vision

/**
 * Modelos de Claude con soporte de visión que se ofrecen en el desplegable de Ajustes. Igual que
 * con Gemini, además de esta lista curada, la pantalla de ajustes deja escribir cualquier otro id
 * a mano ("Personalizado") sin necesitar una actualización de la app.
 */
val ANTHROPIC_MODELS = listOf(
    "claude-opus-5",
    "claude-sonnet-5",
    "claude-haiku-4-5"
)

const val DEFAULT_ANTHROPIC_MODEL = "claude-haiku-4-5"
