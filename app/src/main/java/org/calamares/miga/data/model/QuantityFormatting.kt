package org.calamares.miga.data.model

import org.calamares.miga.R
import org.calamares.miga.L10n
import kotlin.math.roundToInt

/**
 * Formats an ingredient as "quantity unit name", scaled by [scale]. Shared by the recipe screens,
 * text and PDF export and the shopping list.
 *
 * Spanish joins unit and name with "de" ("2 tazas de harina") but only when there is a unit
 * ("3 huevos", not "3 de huevos"); English needs no connector ("2 cups flour").
 */
fun formatIngredientText(
    name: String,
    quantity: Double?,
    unit: String?,
    scale: Double = 1.0,
    language: String = L10n.locale().language
): String {
    val quantityPart = quantity?.let { formatQuantity(it * scale) } ?: return name
    val connector = if (language == "es") " de " else " "
    return if (unit.isNullOrBlank()) "$quantityPart $name" else "$quantityPart $unit$connector$name"
}

/** Rounds to two decimals and drops trailing zeros: 2.0 -> "2", 2.50 -> "2.5", 0.333 -> "0.33". */
fun formatQuantity(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}

/** "200 g flour", with "(optional)" after it when the ingredient can be left out. */
fun Ingredient.displayText(scale: Double = 1.0): String =
    formatIngredientText(name, quantity, unit, scale) + if (optional) " " + L10n.str(R.string.ingredient_optional_suffix) else ""
