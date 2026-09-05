package org.calamares.miga.data.health

import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.vision.VisionProviderType

sealed interface RecipeHealthResult {
    data class Success(val colorLevel: HealthColorLevel, val description: String) : RecipeHealthResult
    data class Error(val reason: String) : RecipeHealthResult
}

/** Analiza lo saludable que es una receta a partir de sus ingredientes y su forma de cocinado. */
interface RecipeHealthClient {
    suspend fun analyzeHealthiness(ingredientsText: String, stepsText: String, apiKey: String, model: String): RecipeHealthResult
}

/** Único proveedor implementado por ahora; añadir uno nuevo es solo un caso más aquí. */
fun healthClientFor(provider: VisionProviderType): RecipeHealthClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiHealthClient
}
