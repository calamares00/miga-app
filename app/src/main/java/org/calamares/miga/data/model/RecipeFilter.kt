package org.calamares.miga.data.model

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
    val sortOption: SortOption = SortOption.NAME_ASC
) {
    val isActive: Boolean
        get() = categoryNames.isNotEmpty() || difficulties.isNotEmpty() ||
            utensils.isNotEmpty() || tags.isNotEmpty() || ingredients.isNotEmpty() || origins.isNotEmpty() || onlyFavorites
}
