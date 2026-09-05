package com.bmo00.miga.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.local.entity.CategoryEntity
import com.bmo00.miga.data.local.entity.TagEntity
import com.bmo00.miga.data.local.entity.UtensilEntity
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeFilter
import com.bmo00.miga.data.model.applyFilter
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SearchResult(
    val recipeId: Long,
    val recipeName: String,
    val bookName: String,
    val photoUri: String?,
    val difficultyLabel: String
)

data class GlobalSearchUiState(
    val isLoading: Boolean = true,
    val results: List<SearchResult> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val availableUtensils: List<String> = emptyList(),
    val availableIngredients: List<String> = emptyList()
)

private data class FilterOptions(
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val utensils: List<UtensilEntity>,
    val ingredientNames: List<String>
)

class GlobalSearchViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _filter = MutableStateFlow(RecipeFilter())
    val filter: StateFlow<RecipeFilter> = _filter

    private val filterOptions = combine(
        repository.observeCategories(),
        repository.observeTags(),
        repository.observeUtensils(),
        repository.observeIngredientNames()
    ) { categories, tags, utensils, ingredientNames -> FilterOptions(categories, tags, utensils, ingredientNames) }

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        repository.observeAllRecipes(),
        _filter,
        filterOptions
    ) { recipes, filter, options ->
        GlobalSearchUiState(
            isLoading = false,
            results = recipes.applyFilter(filter).map { it.toSearchResult() },
            availableCategories = options.categories.map { it.name },
            availableTags = options.tags.map { it.name },
            availableUtensils = options.utensils.map { it.name },
            availableIngredients = options.ingredientNames
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalSearchUiState())

    fun updateQuery(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun applyFilter(newFilter: RecipeFilter) {
        _filter.value = newFilter
    }

    fun clearFilters() {
        _filter.update {
            it.copy(
                categoryNames = emptySet(),
                difficulties = emptySet(),
                utensils = emptySet(),
                tags = emptySet(),
                ingredients = emptySet(),
                onlyFavorites = false
            )
        }
    }
}

private fun Recipe.toSearchResult() = SearchResult(
    recipeId = id,
    recipeName = name,
    bookName = recipeBookName,
    photoUri = photos.firstOrNull { it.isCover }?.uri ?: photos.firstOrNull()?.uri,
    difficultyLabel = difficulty.label
)
