package org.calamares.miga.ui.detail

import org.calamares.miga.data.model.displayText
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.formatIngredientText

/**
 * Shared by RecipeDetailScreen (scaled by servings) and CookModeOverlay (unscaled), as read on screen
 * ("½", "1,2 kg"). Thin wrapper
 * over data/model/QuantityFormatting.kt, which is also used by RecipeExporter and the shopping
 * list.
 */
internal fun formatIngredient(ingredient: Ingredient, scale: Double): String = ingredient.displayText(scale, forReading = true)
