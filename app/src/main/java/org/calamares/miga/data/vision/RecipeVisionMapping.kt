package org.calamares.miga.data.vision

import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.RecipeDraft
import org.calamares.miga.data.model.StepGroup

/**
 * Converts an image recognition result straight into a [RecipeDraft] ready to save, without going
 * through the editor's mutable state (unlike
 * [org.calamares.miga.ui.editor.RecipeEditorViewModel.applyVisionResult]). Used by bulk import,
 * where each photo is saved without interactive review.
 */
fun RecipeVisionResultDto.toRecipeDraft(bookId: Long): RecipeDraft {
    /**
     * Same rule as the interactive editor: the first entry of a comma-separated category is the
     * recipe category and the rest become tags.
     */
    val categoryParts = categoryName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    return RecipeDraft(
        recipeBookId = bookId,
        name = name,
        categoryName = categoryParts.firstOrNull(),
        difficulty = Difficulty.parse(difficulty),
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = cookTimeMinutes,
        servings = servings.coerceIn(1, 99),
        notes = notes,
        source = source,
        isFavorite = false,
        photos = emptyList(),
        ingredientGroups = ingredientGroups.map { group ->
            IngredientGroup(
                name = group.name,
                ingredients = group.ingredients.filter { it.name.isNotBlank() }
                    .map { Ingredient(name = it.name.trim(), quantity = it.quantity, unit = it.unit?.trim()?.takeIf { u -> u.isNotBlank() }) }
            )
        }.ifEmpty { listOf(IngredientGroup(name = null, ingredients = emptyList())) },
        stepGroups = stepGroups.map { group ->
            StepGroup(name = group.name, instructions = group.instructions.map { it.trim() }.filter { it.isNotBlank() })
        }.ifEmpty { listOf(StepGroup(name = null, instructions = emptyList())) },
        tagNames = (tags + categoryParts.drop(1)).distinct(),
        utensilNames = utensils
    )
}
