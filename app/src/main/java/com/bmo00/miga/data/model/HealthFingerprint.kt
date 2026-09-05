package com.bmo00.miga.data.model

import java.security.MessageDigest

/**
 * Huella de ingredientes+pasos usada para invalidar la valoración de salud cacheada de una receta
 * (ver HealthRating) cuando su contenido cambia. Extraída como función pura (sin depender de
 * RecipeRepository/AppDatabase) para poder probarla con un test unitario normal.
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
