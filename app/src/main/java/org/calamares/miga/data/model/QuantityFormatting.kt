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
    language: String = L10n.locale().language,
    /** "½" and "1,25" instead of "0.5" and "1.25", for reading in the app (not for exported text). */
    forReading: Boolean = false
): String {
    val quantityPart = quantity?.let { if (forReading) readableQuantity(it * scale, language) else formatQuantity(it * scale) } ?: return name
    if (unit.isNullOrBlank()) return "$quantityPart $name"
    val connector = if (language == "es") " de " else " "
    return "$quantityPart ${UnitInflection.inflect(unit, quantity * scale, language)}$connector$name"
}

/**
 * The same as [formatIngredientText] in two parts, for a list with the amounts in a column of
 * their own: ("2 tazas", "harina"), (null, "sal"). No "de" between them: the column separates.
 */
fun ingredientParts(
    name: String,
    quantity: Double?,
    unit: String?,
    scale: Double = 1.0,
    language: String = L10n.locale().language,
    forReading: Boolean = false
): Pair<String?, String> {
    val quantityPart = quantity?.let { if (forReading) readableQuantity(it * scale, language) else formatQuantity(it * scale) } ?: return null to name
    if (unit.isNullOrBlank()) return quantityPart to name
    return "$quantityPart ${UnitInflection.inflect(unit, quantity * scale, language)}" to name
}

private val FRACTIONS = mapOf(25 to "¼", 50 to "½", 75 to "¾")

/**
 * An amount as people write it in a recipe: halves and quarters as fractions ("½", "1½"), other
 * decimals with the language's separator ("1,25" in Spanish).
 */
fun readableQuantity(value: Double, language: String = L10n.locale().language): String {
    val hundredths = Math.round(value * 100)
    val whole = hundredths / 100
    FRACTIONS[(hundredths % 100).toInt()]?.let { fraction -> return if (whole == 0L) fraction else "$whole$fraction" }
    val plain = formatQuantity(value)
    return if (language == "es") plain.replace('.', ',') else plain
}

/**
 * Rounds to two decimals and drops trailing zeros: 2.0 -> "2", 2.50 -> "2.5", 0.333 -> "0.33". A
 * positive amount too small for two decimals keeps two significant digits (0.004 -> "0.004")
 * instead of showing "0", which happened when scaling a big recipe down to a few servings.
 */
fun formatQuantity(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    if (rounded == 0.0 && value > 0.0) {
        return java.math.BigDecimal(value).round(java.math.MathContext(2)).stripTrailingZeros().toPlainString()
    }
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
