package org.calamares.miga.data.vision

/** Proveedor de IA. El usuario los ordena por prioridad en Ajustes (ver [org.calamares.miga.data.ai.runAi]). */
enum class VisionProviderType(val label: String) {
    GEMINI("Google Gemini"),
    ANTHROPIC("Anthropic Claude"),
    OPENROUTER("OpenRouter")
}
