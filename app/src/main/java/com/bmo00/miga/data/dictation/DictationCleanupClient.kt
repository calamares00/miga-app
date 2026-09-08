package com.bmo00.miga.data.dictation

import com.bmo00.miga.data.vision.VisionProviderType

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
}

/** Prompt compartido entre los dos proveedores, para que no diverjan. */
internal fun buildDictationCleanupPrompt(rawText: String): String = """
    Se ha dictado por voz el siguiente paso de una receta de cocina, así que puede tener muletillas
    ("eh", "esto", "o sea"), repeticiones, falta de puntuación o una corrección a mitad de frase
    (p. ej. "bueno no, mejor pon..."). Reescríbelo como una única instrucción de receta clara, en
    español, conservando el sentido y el orden de lo dicho, sin añadir información que no esté en
    el texto original ni cambiar cantidades o ingredientes. Devuelve solo el texto final del paso,
    sin comillas ni explicaciones.

    Texto dictado: "$rawText"
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
