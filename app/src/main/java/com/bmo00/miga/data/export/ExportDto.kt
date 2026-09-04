package com.bmo00.miga.data.export

import kotlinx.serialization.Serializable

@Serializable
data class LibraryExportDto(
    val version: Int = 1,
    val exportedAt: Long,
    val recipes: List<RecipeExportDto>
)

@Serializable
data class RecipeExportDto(
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
