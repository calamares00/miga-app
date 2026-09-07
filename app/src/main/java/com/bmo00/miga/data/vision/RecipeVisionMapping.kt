package com.bmo00.miga.data.vision

import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.data.model.Ingredient
import com.bmo00.miga.data.model.IngredientGroup
import com.bmo00.miga.data.model.RecipeDraft
import com.bmo00.miga.data.model.StepGroup

/**
 * Convierte el resultado de reconocimiento por foto directamente en un [RecipeDraft] listo para
 * guardar, sin pasar por el estado mutable del editor (a diferencia de
 * [com.bmo00.miga.ui.editor.RecipeEditorViewModel.applyVisionResult]). Usado por la importación
 * masiva, donde cada foto se guarda directamente sin revisión interactiva.
 */
fun RecipeVisionResultDto.toRecipeDraft(bookId: Long): RecipeDraft {
    // Misma regla que en el editor interactivo: la primera categoría de una lista separada por
    // comas es la categoría de la receta, el resto se añaden como etiquetas.
    val categoryParts = categoryName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    return RecipeDraft(
        recipeBookId = bookId,
        name = name,
        categoryName = categoryParts.firstOrNull(),
        difficulty = runCatching { Difficulty.valueOf(difficulty) }.getOrDefault(Difficulty.MEDIA),
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
