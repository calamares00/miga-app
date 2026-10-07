package org.calamares.miga.data.vision

import org.calamares.miga.L10n
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.KitchenEquipment
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
        utensilNames = KitchenEquipment.clean(utensils, if (L10n.locale().language == "es") "es" else "en")
    )
}

private val DIFFICULTY_ORDER = listOf("EASY", "MEDIUM", "HARD")

/**
 * Several recipes found on the same pages joined into one: each becomes named ingredient and step
 * groups (its own sub-groups keep their names, prefixed with the recipe when there is more than
 * one), times are added up, and tags, equipment and dish photos are combined.
 */
fun List<RecipeVisionResultDto>.mergedIntoOne(): RecipeVisionResultDto {
    if (size == 1) return first()
    fun groupName(recipe: RecipeVisionResultDto, group: String?) = if (group.isNullOrBlank()) recipe.name else "${recipe.name}: $group"
    fun sumOrNull(values: List<Int?>) = values.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    return RecipeVisionResultDto(
        name = joinToString(" · ") { it.name.trim() },
        categoryName = firstNotNullOfOrNull { it.categoryName?.takeIf { name -> name.isNotBlank() } },
        difficulty = maxByOrNull { DIFFICULTY_ORDER.indexOf(Difficulty.parse(it.difficulty).name) }?.difficulty ?: "MEDIUM",
        prepTimeMinutes = sumOrNull(map { it.prepTimeMinutes }),
        cookTimeMinutes = sumOrNull(map { it.cookTimeMinutes }),
        servings = maxOf { it.servings },
        notes = filter { it.notes.isNotBlank() }.joinToString("\n\n") { "${it.name}: ${it.notes.trim()}" },
        source = map { it.source.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · "),
        ingredientGroups = flatMap { recipe ->
            recipe.ingredientGroups.filter { it.ingredients.isNotEmpty() }.map { it.copy(name = groupName(recipe, it.name)) }
        },
        stepGroups = flatMap { recipe ->
            recipe.stepGroups.filter { it.instructions.isNotEmpty() }.map { it.copy(name = groupName(recipe, it.name)) }
        },
        tags = flatMap { it.tags }.distinctBy { it.trim().lowercase() },
        utensils = flatMap { it.utensils }.distinctBy { it.trim().lowercase() },
        dishPhotos = flatMap { it.dishPhotos }.take(3)
    )
}
