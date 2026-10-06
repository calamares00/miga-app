package org.calamares.miga.ui.detail

import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.formatIngredientText

/**
 * Shared by RecipeDetailScreen (scaled by servings) and CookModeOverlay (unscaled). Thin wrapper
 * over data/model/QuantityFormatting.kt, which is also used by RecipeExporter and the shopping
 * list.
 */
internal fun formatIngredient(ingredient: Ingredient, scale: Double): String =
    formatIngredientText(ingredient.name, ingredient.quantity, ingredient.unit, scale)
