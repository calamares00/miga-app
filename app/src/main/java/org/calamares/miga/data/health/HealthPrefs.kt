package org.calamares.miga.data.health

import org.calamares.miga.data.allergens.Allergen

/** Which allergens the recipe page shows (Settings > Health). */
enum class AllergenDisplay {
    /** None on the page; they stay in the recipe menu. */
    NONE,

    /** Only the user's own allergies and intolerances. */
    MINE,

    /** All of them, as before this setting existed: the default. */
    ALL
}

/** What the user chose in Settings > Health, besides carbohydrates. */
data class HealthPrefs(
    val allergenDisplay: AllergenDisplay = AllergenDisplay.ALL,
    val myAllergens: Set<Allergen> = emptySet(),
    /** A warning on recipe cards that have one of [myAllergens]. */
    val warnInLists: Boolean = true,
    /** Searches start with "without" [myAllergens]. */
    val hideMineInSearch: Boolean = false,
    /** The recipe page says what coeliacs need to know. */
    val coeliac: Boolean = false,
    /** Searches start with "for coeliacs". */
    val coeliacInSearch: Boolean = false
) {
    /** The allergens to warn about on recipe cards: none unless the user picked some. */
    val listWarnings: Set<Allergen> get() = if (warnInLists) myAllergens else emptySet()
}
