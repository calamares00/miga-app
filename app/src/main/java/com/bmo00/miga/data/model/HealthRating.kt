package com.bmo00.miga.data.model

enum class HealthColorLevel { GREEN, YELLOW, RED }

/**
 * Valoración de salud de una receta generada por IA a partir de sus ingredientes y su forma de
 * cocinado. [fingerprint] identifica el estado de ingredientes/pasos que se analizó, para poder
 * detectar cuándo ha quedado obsoleta (ver RecipeRepository.computeHealthFingerprint).
 */
data class HealthRating(
    val color: HealthColorLevel,
    val description: String,
    val fingerprint: String,
    val analyzedAt: Long
)
