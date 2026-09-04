package com.bmo00.miga.data.model

data class Recipe(
    val id: Long = 0L,
    val uid: String,
    val recipeBookId: Long,
    val recipeBookName: String,
    val name: String,
    val categoryId: Long?,
    val categoryName: String?,
    val difficulty: Difficulty,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val servings: Int,
    val notes: String,
    val source: String,
    val isFavorite: Boolean,
    val timesCooked: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val photos: List<RecipePhoto>,
    val ingredientGroups: List<IngredientGroup>,
    val stepGroups: List<StepGroup>,
    val tags: List<String>,
    val utensils: List<String>
) {
    val totalTimeMinutes: Int?
        get() = if (prepTimeMinutes == null && cookTimeMinutes == null) {
            null
        } else {
            (prepTimeMinutes ?: 0) + (cookTimeMinutes ?: 0)
        }

    val coverPhotoUri: String?
        get() = photos.firstOrNull { it.isCover }?.uri ?: photos.firstOrNull()?.uri
}

data class RecipePhoto(
    val uri: String,
    val isCover: Boolean
)

data class IngredientGroup(
    /** null = grupo principal de la receta. Un nombre indica una sub-receta (p.ej. "Salsa de tomate"). */
    val name: String?,
    val ingredients: List<Ingredient>
)

data class Ingredient(
    val name: String,
    val quantity: Double?,
    val unit: String?
)

data class StepGroup(
    /** null = grupo principal de la receta. Un nombre indica una sub-receta (p.ej. "Salsa de tomate"). */
    val name: String?,
    val instructions: List<String>
)

data class RecipeSummary(
    val id: Long,
    val name: String,
    val categoryName: String?,
    val coverPhotoUri: String?,
    val difficulty: Difficulty,
    val totalTimeMinutes: Int?,
    val isFavorite: Boolean,
    val timesCooked: Int,
    val tags: List<String>,
    val utensils: List<String>,
    val createdAt: Long,
    val prepTimeMinutes: Int?
)

fun Recipe.toSummary() = RecipeSummary(
    id = id,
    name = name,
    categoryName = categoryName,
    coverPhotoUri = coverPhotoUri,
    difficulty = difficulty,
    totalTimeMinutes = totalTimeMinutes,
    isFavorite = isFavorite,
    timesCooked = timesCooked,
    tags = tags,
    utensils = utensils,
    createdAt = createdAt,
    prepTimeMinutes = prepTimeMinutes
)
