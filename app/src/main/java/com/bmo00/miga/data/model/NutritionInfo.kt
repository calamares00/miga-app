package com.bmo00.miga.data.model

/**
 * Estimación nutricional por ración generada por IA a partir de ingredientes/pasos/raciones,
 * cacheada igual que [HealthRating] (misma huella de invalidación, ver
 * RecipeRepository.computeHealthFingerprint - depende del mismo contenido, así que reutiliza el
 * mismo mecanismo, con su propia columna independiente para poder invalidarse por separado).
 */
data class NutritionInfo(
    val caloriesPerServing: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fingerprint: String,
    val analyzedAt: Long
)
