package org.calamares.miga.data.model

import org.calamares.miga.data.allergens.Allergen

/** Something a recipe is still missing; see the "To complete" statistics. */
enum class MissingField {
    PHOTO, CATEGORY, INGREDIENTS, STEPS, TIME;

    fun isMissingIn(recipe: Recipe): Boolean = when (this) {
        PHOTO -> recipe.photos.isEmpty()
        CATEGORY -> recipe.categoryName.isNullOrBlank()
        INGREDIENTS -> recipe.ingredientGroups.all { it.ingredients.isEmpty() }
        STEPS -> recipe.stepGroups.all { it.instructions.isEmpty() }
        TIME -> recipe.totalTimeMinutes == null || recipe.totalTimeMinutes == 0
    }
}

data class RecipeFilter(
    val query: String = "",
    val categoryNames: Set<String> = emptySet(),
    val difficulties: Set<Difficulty> = emptySet(),
    val utensils: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val ingredients: Set<String> = emptySet(),
    /** Country codes of the recipes' origin (see RecipeOrigin). */
    val origins: Set<String> = emptySet(),
    val onlyFavorites: Boolean = false,
    /** Recipes without these allergens (see AllergenAnalysis). */
    val withoutAllergens: Set<Allergen> = emptySet(),
    /**
     * With [withoutAllergens], also keep recipes where the allergen only depends on the product
     * bought (stock cube, sausages...). Off by default: the safe answer.
     */
    val allowMayContain: Boolean = false,
    /**
     * Recipes a coeliac can cook: no ingredient that always has gluten. Those whose gluten depends
     * on the product stay ("Suitable for coeliacs if you buy gluten-free...").
     */
    val coeliacFriendly: Boolean = false,
    val sortOption: SortOption = SortOption.NAME_ASC,
    // The conditions below come from the statistics screen (tapping a figure lists its recipes);
    // the filter sheet does not edit them, they are shown as chips that can be removed.
    /** Names of the books the recipes are in. */
    val bookNames: Set<String> = emptySet(),
    /** Recipes missing all of these. */
    val missing: Set<MissingField> = emptySet(),
    /** Recipes whose total time is known and at most this many minutes. */
    val maxMinutes: Int? = null,
    val onlyCooked: Boolean = false,
    val onlyRated: Boolean = false,
    /** Recipes added at or after this time (epoch millis). */
    val addedSince: Long? = null
) {
    /** True when a condition from the statistics screen is applied (see [bookNames]). */
    val hasStatsConditions: Boolean
        get() = bookNames.isNotEmpty() || missing.isNotEmpty() || maxMinutes != null || onlyCooked || onlyRated || addedSince != null

    val isActive: Boolean
        get() = categoryNames.isNotEmpty() || difficulties.isNotEmpty() ||
            utensils.isNotEmpty() || tags.isNotEmpty() || ingredients.isNotEmpty() || origins.isNotEmpty() || onlyFavorites ||
            withoutAllergens.isNotEmpty() || coeliacFriendly ||
            hasStatsConditions

    /**
     * How many conditions are applied, for the badge of the filter button. [ignoreFavorites] on the
     * Favourites tab, where "only favourites" is implicit.
     */
    fun activeConditions(ignoreFavorites: Boolean = false): Int =
        categoryNames.size + difficulties.size + utensils.size + tags.size + ingredients.size + origins.size + withoutAllergens.size +
            bookNames.size + missing.size +
            listOf(onlyFavorites && !ignoreFavorites, coeliacFriendly, maxMinutes != null, onlyCooked, onlyRated, addedSince != null).count { it }

    /** This filter without the conditions from the statistics screen. */
    fun withoutStatsConditions(): RecipeFilter =
        copy(bookNames = emptySet(), missing = emptySet(), maxMinutes = null, onlyCooked = false, onlyRated = false, addedSince = null)
}
