package org.calamares.miga.ui.detail

import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.formatIngredientText

/**
 * Compartido entre RecipeDetailScreen (con reescalado por raciones) y CookModeOverlay (sin
 * reescalar). Wrapper fino sobre data/model/QuantityFormatting.kt (formato compartido con
 * RecipeExporter y la lista de la compra), que se mantiene aquí solo para no tocar la firma
 * `formatIngredient(ingredient, scale)` en sus sitios de uso.
 */
internal fun formatIngredient(ingredient: Ingredient, scale: Double): String =
    formatIngredientText(ingredient.name, ingredient.quantity, ingredient.unit, scale)
