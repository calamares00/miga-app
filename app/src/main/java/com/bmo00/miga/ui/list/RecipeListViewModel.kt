package com.bmo00.miga.ui.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.export.RecipeExporter
import com.bmo00.miga.data.local.entity.CategoryEntity
import com.bmo00.miga.data.local.entity.TagEntity
import com.bmo00.miga.data.local.entity.UtensilEntity
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeFilter
import com.bmo00.miga.data.model.RecipeSummary
import com.bmo00.miga.data.model.SortOption
import com.bmo00.miga.data.model.toSummary
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeGroup(
    val categoryName: String,
    val recipes: List<RecipeSummary>
)

data class RecipeListUiState(
    val isLoading: Boolean = true,
    val bookName: String = "",
    val groups: List<RecipeGroup> = emptyList(),
    val totalCount: Int = 0,
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val availableUtensils: List<String> = emptyList()
)

private data class RecipeListBase(
    val recipes: List<Recipe>,
    val filter: RecipeFilter,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val utensils: List<UtensilEntity>
)

private const val UNCATEGORIZED = "Sin categoría"

class RecipeListViewModel(private val repository: RecipeRepository, private val bookId: Long) : ViewModel() {

    private val _filter = MutableStateFlow(RecipeFilter())
    val filter: StateFlow<RecipeFilter> = _filter

    private val base = combine(
        repository.observeRecipesForBook(bookId),
        _filter,
        repository.observeCategories(),
        repository.observeTags(),
        repository.observeUtensils()
    ) { recipes, filter, categories, tags, utensils -> RecipeListBase(recipes, filter, categories, tags, utensils) }

    val uiState: StateFlow<RecipeListUiState> = combine(base, repository.observeRecipeBook(bookId)) { data, book ->
        RecipeListUiState(
            isLoading = false,
            bookName = book?.name.orEmpty(),
            groups = buildGroups(data.recipes, data.filter),
            totalCount = data.recipes.size,
            availableCategories = data.categories.map { it.name },
            availableTags = data.tags.map { it.name },
            availableUtensils = data.utensils.map { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeListUiState())

    fun updateQuery(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun applyFilter(newFilter: RecipeFilter) {
        _filter.value = newFilter
    }

    fun clearFilters() {
        _filter.update { it.copy(categoryNames = emptySet(), difficulties = emptySet(), utensils = emptySet(), tags = emptySet(), onlyFavorites = false) }
    }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { repository.toggleFavorite(id, !current) }
    }

    fun deleteRecipe(id: Long) {
        viewModelScope.launch { repository.deleteRecipe(id) }
    }

    fun exportBookAsJson(context: Context) {
        viewModelScope.launch {
            val book = repository.getRecipeBookOnce(bookId) ?: return@launch
            val recipes = repository.getRecipesForBookOnce(bookId)
            RecipeExporter.shareBookAsJson(context, book, recipes)
        }
    }

    fun exportBookAsPdf(context: Context) {
        viewModelScope.launch {
            val book = repository.getRecipeBookOnce(bookId) ?: return@launch
            val recipes = repository.getRecipesForBookOnce(bookId)
            RecipeExporter.shareBookAsPdf(context, book, recipes)
        }
    }

    private fun buildGroups(recipes: List<Recipe>, filter: RecipeFilter): List<RecipeGroup> {
        val query = filter.query.trim().lowercase()

        val filtered = recipes.filter { recipe ->
            val matchesQuery = query.isEmpty() ||
                recipe.name.lowercase().contains(query) ||
                recipe.tags.any { it.lowercase().contains(query) } ||
                recipe.ingredientGroups.any { group -> group.ingredients.any { it.name.lowercase().contains(query) } }

            val matchesCategory = filter.categoryNames.isEmpty() ||
                filter.categoryNames.contains(recipe.categoryName ?: UNCATEGORIZED)
            val matchesDifficulty = filter.difficulties.isEmpty() || filter.difficulties.contains(recipe.difficulty)
            val matchesUtensils = filter.utensils.isEmpty() || filter.utensils.all { it in recipe.utensils }
            val matchesTags = filter.tags.isEmpty() || filter.tags.all { it in recipe.tags }
            val matchesFavorite = !filter.onlyFavorites || recipe.isFavorite

            matchesQuery && matchesCategory && matchesDifficulty && matchesUtensils && matchesTags && matchesFavorite
        }

        val sorted = when (filter.sortOption) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOption.RECENT -> filtered.sortedByDescending { it.createdAt }
            SortOption.MOST_COOKED -> filtered.sortedByDescending { it.timesCooked }
            SortOption.PREP_TIME -> filtered.sortedBy { it.prepTimeMinutes ?: Int.MAX_VALUE }
        }

        return sorted
            .groupBy { it.categoryName ?: UNCATEGORIZED }
            .toSortedMap(compareBy { if (it == UNCATEGORIZED) "￿" else it.lowercase() })
            .map { (category, recipesInGroup) -> RecipeGroup(category, recipesInGroup.map { it.toSummary() }) }
    }
}
