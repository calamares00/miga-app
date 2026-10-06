package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R
import java.text.Normalizer

/** Group name used for recipes without a category, in the current app language. */
val UNCATEGORIZED_CATEGORY_LABEL: String get() = L10n.str(R.string.uncategorized)

/**
 * Filters and sorts recipes with [filter]. Shared by the book recipe list and the global search so
 * both agree on what counts as a match. Text search ignores case and accents ("cafe" finds "Café").
 */
fun List<Recipe>.applyFilter(filter: RecipeFilter): List<Recipe> {
    val query = searchKey(filter.query.trim())

    val filtered = this.filter { recipe ->
        val matchesQuery = query.isEmpty() ||
            searchKey(recipe.name).contains(query) ||
            recipe.tags.any { searchKey(it).contains(query) } ||
            recipe.ingredientGroups.any { group -> group.ingredients.any { searchKey(it.name).contains(query) } }

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
        SortOption.BEST_RATED -> filtered.sortedByDescending { it.rating ?: -1 }
    }
}

/** Lowercase text without diacritics, for accent-insensitive search ("Café" -> "cafe"). */
fun searchKey(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

private val DIACRITICS = Regex("\\p{Mn}+")
