package com.bmo00.miga.data.export

import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.data.model.Ingredient
import com.bmo00.miga.data.model.IngredientGroup
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeDraft
import com.bmo00.miga.data.model.StepGroup

fun Recipe.toExportDto() = RecipeExportDto(
    version = CURRENT_RECIPE_SCHEMA_VERSION,
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
    utensils = utensils
)

/** [recipeBookId] debe resolverse antes (buscar/crear el libro por [RecipeExportDto.recipeBookName]). */
fun RecipeExportDto.toDraft(recipeBookId: Long) = RecipeDraft(
    id = 0L,
    recipeBookId = recipeBookId,
    name = name,
    categoryName = categoryName,
    difficulty = runCatching { Difficulty.valueOf(difficulty) }.getOrDefault(Difficulty.MEDIA),
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    notes = notes,
    source = source,
    isFavorite = isFavorite,
    photos = emptyList(),
    ingredientGroups = ingredientGroups.map { dto ->
        IngredientGroup(dto.name, dto.ingredients.map { Ingredient(it.name, it.quantity, it.unit) })
    },
    stepGroups = stepGroups.map { dto -> StepGroup(dto.name, dto.instructions) },
    tagNames = tags,
    utensilNames = utensils
)
