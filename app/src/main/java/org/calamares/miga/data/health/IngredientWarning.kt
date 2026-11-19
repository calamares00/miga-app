package org.calamares.miga.data.health

import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.allergens.AllergenAnalysis
import org.calamares.miga.data.allergens.AllergenLevel

/** The warning next to an ingredient: how sure, and which allergens. */
data class IngredientWarning(val level: AllergenLevel, val allergens: List<Allergen>)

/**
 * The warning for one ingredient, or null for none. With [watched] null (all allergens shown),
 * only what depends on the product is flagged: the allergen row already says what the recipe
 * has. With a set (the user's own), anything of theirs the ingredient has or may have.
 */
fun ingredientWarning(name: String, watched: Set<Allergen>?): IngredientWarning? {
    if (watched != null && watched.isEmpty()) return null
    val found = AllergenAnalysis.ofIngredient(name).levels
    val relevant = found.filter { (allergen, level) ->
        if (watched == null) level == AllergenLevel.MAY_CONTAIN else allergen in watched && level != AllergenLevel.FREE
    }
    if (relevant.isEmpty()) return null
    return IngredientWarning(relevant.values.max(), Allergen.entries.filter { it in relevant })
}
