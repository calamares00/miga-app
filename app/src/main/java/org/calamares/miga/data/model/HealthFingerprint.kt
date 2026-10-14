package org.calamares.miga.data.model

import java.security.MessageDigest

/**
 * Fingerprint of a recipe's ingredients and steps, used to invalidate the cached AI health rating
 * and nutrition estimate when the content changes. A pure function so it can be unit tested.
 */
object HealthFingerprint {
    fun compute(ingredientGroups: List<IngredientGroup>, stepGroups: List<StepGroup>): String {
        val canonical = buildString {
            ingredientGroups.forEach { group ->
                append(group.name.orEmpty()).append('|')
                group.ingredients.forEach { ingredient ->
                    append(ingredient.name).append(',').append(ingredient.quantity).append(',').append(ingredient.unit.orEmpty()).append(';')
                }
            }
            append("##")
            stepGroups.forEach { group ->
                append(group.name.orEmpty()).append('|')
                group.instructions.forEach { append(it).append(';') }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
