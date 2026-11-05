package org.calamares.miga.data.model

import org.calamares.miga.R
import org.calamares.miga.L10n
import kotlin.math.roundToInt

/**
 * Formats an ingredient as "quantity unit name", scaled by [scale]. Shared by the recipe screens,
 * text and PDF export and the shopping list.
 *
 * The unit agrees with the quantity shown ("1 taza", "3 tazas", see [UnitInflection]). Spanish
 * joins unit and name with "de" ("2 tazas de harina") but only when there is a unit ("3 huevos",
 * not "3 de huevos"); English needs no connector ("2 cups flour").
 */
fun formatIngredientText(
    name: String,
    quantity: Double?,
    unit: String?,
    scale: Double = 1.0,
    language: String = L10n.locale().language
): String {
    val quantityPart = quantity?.let { formatQuantity(it * scale) } ?: return name
    if (unit.isNullOrBlank()) return "$quantityPart $name"
    val connector = if (language == "es") " de " else " "
    return "$quantityPart ${UnitInflection.inflect(unit, quantityPart.toDouble(), language)}$connector$name"
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

/**
 * A time in minutes as people read it: "45 min", "1 h", "3 h 50 min". From 60 minutes on it goes to
 * hours. "h" and "min" are the same in Spanish and English, so no translation is needed.
 */
fun formatDuration(minutes: Int): String {
    val total = minutes.coerceAtLeast(0)
    val hours = total / 60
    val rest = total % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}

/** "200 g flour", with "(optional)" after it when the ingredient can be left out. */
fun Ingredient.displayText(scale: Double = 1.0): String =
    formatIngredientText(name, quantity, unit, scale) + if (optional) " " + L10n.str(R.string.ingredient_optional_suffix) else ""
