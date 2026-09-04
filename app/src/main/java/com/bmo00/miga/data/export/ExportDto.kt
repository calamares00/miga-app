package com.bmo00.miga.data.export

import kotlinx.serialization.Serializable

/**
 * Versión del esquema de cada JSON exportado. Un archivo sin la clave "version" (todo lo
 * exportado antes de que existiera este campo) se trata como versión 0; ver
 * [RecipeExporter] para las migraciones que llevan un JSON antiguo hasta la versión actual.
 */
const val CURRENT_RECIPE_SCHEMA_VERSION = 1
const val CURRENT_LIBRARY_SCHEMA_VERSION = 1

@Serializable
data class LibraryExportDto(
    val version: Int = CURRENT_LIBRARY_SCHEMA_VERSION,
    val exportedAt: Long,
    val recipes: List<RecipeExportDto>
)

@Serializable
data class RecipeExportDto(
    val version: Int = CURRENT_RECIPE_SCHEMA_VERSION,
    val name: String,
    val recipeBookName: String,
    val categoryName: String?,
    val difficulty: String,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val servings: Int,
    val notes: String,
    val source: String,
    val isFavorite: Boolean,
    val ingredientGroups: List<IngredientGroupDto>,
    val stepGroups: List<StepGroupDto>,
    val tags: List<String>,
    val utensils: List<String>
)

@Serializable
data class IngredientGroupDto(
    val name: String?,
    val ingredients: List<IngredientDto>
)

@Serializable
data class IngredientDto(
    val name: String,
    val quantity: Double?,
    val unit: String?
)

@Serializable
data class StepGroupDto(
    val name: String?,
    val instructions: List<String>
)
