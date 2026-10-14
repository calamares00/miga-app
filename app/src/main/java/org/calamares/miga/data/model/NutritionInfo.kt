package org.calamares.miga.data.model

/**
 * AI nutrition estimate per serving, cached like [HealthRating] and invalidated with the same
 * content fingerprint, but stored in its own columns so each can be refreshed separately.
 */
data class NutritionInfo(
    val caloriesPerServing: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fingerprint: String,
    val analyzedAt: Long
)
