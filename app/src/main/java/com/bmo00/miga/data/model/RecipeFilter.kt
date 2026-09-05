package com.bmo00.miga.data.model

data class RecipeFilter(
    val query: String = "",
    val categoryNames: Set<String> = emptySet(),
    val difficulties: Set<Difficulty> = emptySet(),
    val utensils: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val ingredients: Set<String> = emptySet(),
    val onlyFavorites: Boolean = false,
    val sortOption: SortOption = SortOption.NAME_ASC
) {
    val isActive: Boolean
        get() = categoryNames.isNotEmpty() || difficulties.isNotEmpty() ||
            utensils.isNotEmpty() || tags.isNotEmpty() || ingredients.isNotEmpty() || onlyFavorites
}
