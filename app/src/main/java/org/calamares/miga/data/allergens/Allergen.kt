package org.calamares.miga.data.allergens

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * The 14 allergens EU law requires to be declared (Regulation (EU) No 1169/2011, Annex II), in
 * that order, plus lactose, an intolerance worked out from milk. Constant names may be stored, so
 * they must not be renamed.
 */
enum class Allergen(private val labelRes: Int, val iconRes: Int) {
    GLUTEN(R.string.allergen_gluten, R.drawable.ic_allergen_gluten),
    CRUSTACEANS(R.string.allergen_crustaceans, R.drawable.ic_allergen_crustaceans),
    EGGS(R.string.allergen_eggs, R.drawable.ic_allergen_eggs),
    FISH(R.string.allergen_fish, R.drawable.ic_allergen_fish),
    PEANUTS(R.string.allergen_peanuts, R.drawable.ic_allergen_peanuts),
    SOY(R.string.allergen_soy, R.drawable.ic_allergen_soy),
    MILK(R.string.allergen_milk, R.drawable.ic_allergen_milk),
    TREE_NUTS(R.string.allergen_tree_nuts, R.drawable.ic_allergen_tree_nuts),
    CELERY(R.string.allergen_celery, R.drawable.ic_allergen_celery),
    MUSTARD(R.string.allergen_mustard, R.drawable.ic_allergen_mustard),
    SESAME(R.string.allergen_sesame, R.drawable.ic_allergen_sesame),
    SULPHITES(R.string.allergen_sulphites, R.drawable.ic_allergen_sulphites),
    LUPIN(R.string.allergen_lupin, R.drawable.ic_allergen_lupin),
    MOLLUSCS(R.string.allergen_molluscs, R.drawable.ic_allergen_molluscs),

    /** Not one of the 14: an intolerance, derived from milk. */
    LACTOSE(R.string.allergen_lactose, R.drawable.ic_allergen_lactose);

    val label: String get() = L10n.str(labelRes)
}

/**
 * How a recipe (or an ingredient) stands with an allergen, from best to worst. A recipe takes the
 * worst level of its ingredients.
 */
enum class AllergenLevel {
    /** No ingredient brings it. */
    FREE,

    /**
     * Brought by the product the cook buys, not by the food itself (stock cubes, sausages, soy
     * sauce, oats...), or traces from cross-contact: precautionary labels say "may contain".
     */
    MAY_CONTAIN,

    /** An ingredient always brings it. */
    CONTAINS
}
