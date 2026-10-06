package org.calamares.miga.data.dictation

import org.calamares.miga.data.ai.OpenRouterDictationCleanupClient
import org.calamares.miga.data.vision.VisionProviderType

sealed interface DictationCleanupResult {
    data class Success(val text: String) : DictationCleanupResult
    data class Error(val reason: String) : DictationCleanupResult
}

/** Limpia el texto en bruto de un paso de receta dictado por voz (quita muletillas, corrige
 *  puntuación) usando el proveedor de IA ya configurado en Ajustes - el reconocimiento de voz en
 *  sí es local (ver SpeechDictation), este cliente solo pule el resultado. A diferencia de
 *  RecipeHealthClient no pide JSON: la salida es un único texto libre, así que forzar un formato
 *  estructurado aquí solo añadiría un motivo más de fallo sin ningún beneficio. */
interface DictationCleanupClient {
    suspend fun cleanUp(rawText: String, apiKey: String, model: String): DictationCleanupResult
}

fun dictationCleanupClientFor(provider: VisionProviderType): DictationCleanupClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiDictationCleanupClient
    VisionProviderType.ANTHROPIC -> AnthropicDictationCleanupClient
    VisionProviderType.OPENROUTER -> OpenRouterDictationCleanupClient
}

/** Prompt shared by every provider so they all behave the same. */
internal fun buildDictationCleanupPrompt(rawText: String): String = """
    The following step of a cooking recipe was dictated by voice, so it may contain filler words,
    repetitions, missing punctuation or a correction halfway through a sentence (for example
    "no wait, better put..."). Rewrite it as a single clear recipe instruction in the same language
    it was dictated in (do not translate it), keeping the meaning and the order of what was said.
    Do not add information that is not in the original text and do not change quantities or
    ingredients. Return only the final text of the step, without quotes or explanations.

    Dictated text: "$rawText"
""".trimIndent()

/** Quita comillas envolventes que el modelo a veces añade pese a que el prompt pide no ponerlas. */
internal fun stripSurroundingQuotes(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
        trimmed.substring(1, trimmed.length - 1).trim()
    } else {
        trimmed
    }
}
