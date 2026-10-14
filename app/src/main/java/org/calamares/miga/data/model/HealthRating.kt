package org.calamares.miga.data.model

enum class HealthColorLevel { GREEN, YELLOW, RED }

/**
 * AI rating of how healthy a recipe is. [fingerprint] identifies the analysed ingredients and
 * steps, so a stale rating can be detected (see RecipeRepository.computeHealthFingerprint).
 */
data class HealthRating(
    val color: HealthColorLevel,
    val description: String,
    val fingerprint: String,
    val analyzedAt: Long
)
