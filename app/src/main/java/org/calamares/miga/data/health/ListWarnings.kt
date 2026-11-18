package org.calamares.miga.data.health

import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.allergens.AllergenAnalysis
import org.calamares.miga.data.allergens.AllergenLevel
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeFilter

/** The allergens of [mine] this recipe has or may have, to warn about on its card. */
fun Recipe.allergenWarnings(mine: Set<Allergen>): List<Allergen> {
    if (mine.isEmpty()) return emptyList()
    val allergens = AllergenAnalysis.ofRecipe(this)
    return Allergen.entries.filter { it in mine && allergens.level(it) != AllergenLevel.FREE }
}

/** [filter] with the conditions searches start with (Settings > Health). */
fun RecipeFilter.withHealthDefaults(health: HealthPrefs): RecipeFilter = copy(
    withoutAllergens = if (health.hideMineInSearch) withoutAllergens + health.myAllergens else withoutAllergens,
    coeliacFriendly = coeliacFriendly || health.coeliacInSearch
)
