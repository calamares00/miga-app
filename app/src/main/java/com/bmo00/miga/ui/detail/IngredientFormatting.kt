package com.bmo00.miga.ui.detail

import com.bmo00.miga.data.model.Ingredient
import kotlin.math.roundToInt

/** Compartido entre RecipeDetailScreen (con reescalado por raciones) y CookModeOverlay (sin reescalar). */
internal fun formatIngredient(ingredient: Ingredient, scale: Double): String {
    val quantityPart = ingredient.quantity?.let { formatQuantity(it * scale) }
    return buildString {
        if (quantityPart != null) {
            append(quantityPart)
            if (!ingredient.unit.isNullOrBlank()) append(" ${ingredient.unit}")
            append(" de ")
        }
        append(ingredient.name)
    }
}

internal fun formatQuantity(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
