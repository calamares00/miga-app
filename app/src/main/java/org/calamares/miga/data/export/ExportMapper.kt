package org.calamares.miga.data.export

import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeDraft
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.StepGroup

fun Recipe.toExportDto() = RecipeExportDto(
    version = CURRENT_RECIPE_SCHEMA_VERSION,
    uid = uid,
    name = name,
    recipeBookName = recipeBookName,
    categoryName = categoryName,
    difficulty = difficulty.name,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    notes = notes,
    source = source,
    isFavorite = isFavorite,
    ingredientGroups = ingredientGroups.map { group ->
        IngredientGroupDto(group.name, group.ingredients.map { IngredientDto(it.name, it.quantity, it.unit) })
    },
    stepGroups = stepGroups.map { group -> StepGroupDto(group.name, group.instructions) },
    tags = tags,
    utensils = utensils,
    photos = photos.mapIndexed { index, photo -> PhotoExportDto(fileName = "$index.jpg", isCover = photo.isCover) },
    health = healthRating?.let { RecipeHealthDto(it.color.name, it.description, it.fingerprint, it.analyzedAt) },
    nutrition = nutritionInfo?.let {
        RecipeNutritionDto(it.caloriesPerServing, it.proteinGrams, it.carbsGrams, it.fatGrams, it.fingerprint, it.analyzedAt)
    },
    rating = rating
)

/**
 * Converts an imported recipe into a draft for [recipeBookId], which must already be resolved from
 * [RecipeExportDto.recipeBookName]. [photos] are the photos already copied to internal storage
 * (empty for a plain JSON import).
 */
fun RecipeExportDto.toDraft(recipeBookId: Long, photos: List<RecipePhoto> = emptyList()) = RecipeDraft(
    id = 0L,
    uid = uid,
    recipeBookId = recipeBookId,
    name = name,
    categoryName = categoryName,
    difficulty = Difficulty.parse(difficulty),
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    notes = notes,
    source = source,
    isFavorite = isFavorite,
    photos = photos,
    ingredientGroups = ingredientGroups.map { dto ->
        IngredientGroup(dto.name, dto.ingredients.map { Ingredient(it.name, it.quantity, it.unit) })
    },
    stepGroups = stepGroups.map { dto -> StepGroup(dto.name, dto.instructions) },
    tagNames = tags,
    utensilNames = utensils
)
