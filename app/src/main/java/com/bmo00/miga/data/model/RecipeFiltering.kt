package com.bmo00.miga.data.model

const val UNCATEGORIZED_CATEGORY_LABEL = "Sin categoría"

/**
 * Filtra y ordena [this] según [filter]; usado tanto en la lista de un libro como en la
 * búsqueda global de recetas, para que ambas coincidan en qué cuenta como una coincidencia.
 */
fun List<Recipe>.applyFilter(filter: RecipeFilter): List<Recipe> {
    val query = filter.query.trim().lowercase()

    val filtered = this.filter { recipe ->
        val matchesQuery = query.isEmpty() ||
            recipe.name.lowercase().contains(query) ||
            recipe.tags.any { it.lowercase().contains(query) } ||
            recipe.ingredientGroups.any { group -> group.ingredients.any { it.name.lowercase().contains(query) } }

        val matchesCategory = filter.categoryNames.isEmpty() ||
            filter.categoryNames.contains(recipe.categoryName ?: UNCATEGORIZED_CATEGORY_LABEL)
        val matchesDifficulty = filter.difficulties.isEmpty() || filter.difficulties.contains(recipe.difficulty)
        val matchesUtensils = filter.utensils.isEmpty() || filter.utensils.all { it in recipe.utensils }
        val matchesTags = filter.tags.isEmpty() || filter.tags.all { it in recipe.tags }
        val matchesIngredients = filter.ingredients.isEmpty() || filter.ingredients.all { wanted ->
            recipe.ingredientGroups.any { group -> group.ingredients.any { it.name.equals(wanted, ignoreCase = true) } }
        }
        val matchesFavorite = !filter.onlyFavorites || recipe.isFavorite

        matchesQuery && matchesCategory && matchesDifficulty && matchesUtensils && matchesTags && matchesIngredients && matchesFavorite
    }

    return when (filter.sortOption) {
        SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
        SortOption.RECENT -> filtered.sortedByDescending { it.createdAt }
        SortOption.MOST_COOKED -> filtered.sortedByDescending { it.timesCooked }
        SortOption.PREP_TIME -> filtered.sortedBy { it.prepTimeMinutes ?: Int.MAX_VALUE }
    }
}
