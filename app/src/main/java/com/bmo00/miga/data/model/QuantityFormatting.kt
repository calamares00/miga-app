package com.bmo00.miga.data.model

import kotlin.math.roundToInt

/**
 * Formato compartido de cantidad+unidad+nombre. Usado en la receta (RecipeDetailScreen,
 * CookModeOverlay, vía el wrapper interno de ui/detail/IngredientFormatting.kt), al exportar a
 * texto (RecipeExporter) y en la lista de la compra (ShoppingListScreen). Antes había dos copias
 * divergentes de esta lógica (una sin el redondeo de [formatQuantity]); unificado aquí.
 */
fun formatIngredientText(name: String, quantity: Double?, unit: String?, scale: Double = 1.0): String {
    val quantityPart = quantity?.let { formatQuantity(it * scale) }
    return buildString {
        if (quantityPart != null) {
            append(quantityPart)
            if (!unit.isNullOrBlank()) append(" $unit")
            append(" de ")
        }
        append(name)
    }
}

fun formatQuantity(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
