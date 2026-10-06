package org.calamares.miga.data.ai

import kotlinx.coroutines.flow.first
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.support.ErrorDetail
import org.calamares.miga.data.vision.VisionProviderType

/** Un proveedor listo para usar: tiene clave y modelo. */
data class AiCandidate(val provider: VisionProviderType, val apiKey: String, val model: String)

/** Proveedores con clave y modelo, en el orden de prioridad elegido en Ajustes. */
suspend fun SettingsRepository.aiCandidates(): List<AiCandidate> =
    observeProviderOrder().first().mapNotNull { provider ->
        val key = apiKeyFor(provider)
        val model = modelFor(provider)
        if (key.isBlank() || model.isBlank()) null else AiCandidate(provider, key, model)
    }

/**
 * Ejecuta una función de IA probando los proveedores configurados en orden de prioridad:
 * si uno falla por un error del proveedor (cuota, clave, red, respuesta no válida...) se pasa al
 * siguiente. Los resultados "normales" (p. ej. "no se ha reconocido ninguna receta") no provocan
 * reintento, porque otro modelo diría lo mismo.
 *
 * - [needsImages]: se saltan los proveedores cuyo modelo no puede leer imágenes.
 * - [errorOf]: extrae el motivo si [T] es un error, o null si es un éxito.
 * - [error]: construye un [T] de error (para "ningún proveedor lee imágenes" o el resumen final).
 *
 * Devuelve null si no hay ningún proveedor configurado.
 */
suspend fun <T> SettingsRepository.runAi(
    needsImages: Boolean = false,
    errorOf: (T) -> String?,
    error: (String) -> T,
    call: suspend (AiCandidate) -> T
): T? {
    val configured = aiCandidates()
    if (configured.isEmpty()) return null
    val usable = if (needsImages) configured.filter { supportsImages(it.provider) } else configured
    if (usable.isEmpty()) return error(L10n.str(R.string.ai_no_image_provider))

    val failures = mutableListOf<Pair<AiCandidate, String>>()
    for (candidate in usable) {
        val result = call(candidate)
        val reason = errorOf(result) ?: return result
        if (!ErrorDetail.isAiError(reason)) return result
        failures += candidate to reason
    }
    if (failures.size == 1) return error(failures.single().second)
    val detail = failures.joinToString("\n\n") { (candidate, reason) ->
        "— ${candidate.provider.label} · ${candidate.model}\n${ErrorDetail.summary(reason)}" +
            (ErrorDetail.detail(reason)?.let { "\n$it" } ?: "")
    }
    val summary = L10n.str(R.string.ai_all_providers_failed, ErrorDetail.summary(failures.last().second))
    return error(ErrorDetail.markAsAi(ErrorDetail.withDetail(summary, detail)))
}
