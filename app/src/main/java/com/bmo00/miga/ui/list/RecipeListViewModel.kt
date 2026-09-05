package com.bmo00.miga.ui.list

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.export.RecipeExporter
import com.bmo00.miga.data.export.RecipeImportResult
import com.bmo00.miga.data.export.toDraft
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.local.entity.CategoryEntity
import com.bmo00.miga.data.local.entity.TagEntity
import com.bmo00.miga.data.local.entity.UtensilEntity
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeFilter
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.model.RecipeSummary
import com.bmo00.miga.data.model.UNCATEGORIZED_CATEGORY_LABEL
import com.bmo00.miga.data.model.applyFilter
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
    val availableUtensils: List<String> = emptyList(),
    val availableIngredients: List<String> = emptyList()
)

private data class FilterOptions(
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val utensils: List<UtensilEntity>,
    val ingredientNames: List<String>
)

private data class RecipeListBase(
    val recipes: List<Recipe>,
    val filter: RecipeFilter,
    val options: FilterOptions
)

class RecipeListViewModel(
    private val repository: RecipeRepository,
    private val bookId: Long,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(RecipeFilter())
    val filter: StateFlow<RecipeFilter> = _filter

    val viewMode: StateFlow<RecipeListViewMode> = settingsRepository.observeRecipeListViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeListViewMode.NORMAL)

    fun setViewMode(mode: RecipeListViewMode) {
        viewModelScope.launch { settingsRepository.setRecipeListViewMode(mode) }
    }

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    fun toggleSelection(id: Long) {
        _selectedIds.update { current -> if (id in current) current - id else current + id }
    }

    fun startSelection(id: Long) {
        _selectedIds.value = setOf(id)
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { repository.deleteRecipe(it) }
            _selectedIds.value = emptySet()
        }
    }

    fun exportSelected(context: Context) {
        viewModelScope.launch {
            val ids = _selectedIds.value
            val book = repository.getRecipeBookOnce(bookId)
            val recipes = repository.getRecipesForBookOnce(bookId).filter { it.id in ids }
            if (recipes.isNotEmpty()) {
                RecipeExporter.shareRecipes(context, "recetas_seleccionadas", book, recipes)
            }
            _selectedIds.value = emptySet()
        }
    }

    fun importRecipeFile(context: Context, uri: Uri, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = RecipeExporter.importRecipe(context, uri)) {
                is RecipeImportResult.Success -> {
                    repository.saveRecipe(result.recipe.toDraft(bookId, result.photos))
                    onMessage("Receta importada")
                }
                is RecipeImportResult.Error -> onMessage("No se pudo importar: ${result.reason}")
            }
        }
    }

    private val filterOptions = combine(
        repository.observeCategories(),
        repository.observeTags(),
        repository.observeUtensils(),
        repository.observeIngredientNames()
    ) { categories, tags, utensils, ingredientNames -> FilterOptions(categories, tags, utensils, ingredientNames) }

    private val base = combine(
        repository.observeRecipesForBook(bookId),
        _filter,
        filterOptions
    ) { recipes, filter, options -> RecipeListBase(recipes, filter, options) }

    val uiState: StateFlow<RecipeListUiState> = combine(base, repository.observeRecipeBook(bookId)) { data, book ->
        RecipeListUiState(
            isLoading = false,
            bookName = book?.name.orEmpty(),
            groups = buildGroups(data.recipes, data.filter),
            totalCount = data.recipes.size,
            availableCategories = data.options.categories.map { it.name },
            availableTags = data.options.tags.map { it.name },
            availableUtensils = data.options.utensils.map { it.name },
            availableIngredients = data.options.ingredientNames
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeListUiState())

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

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { repository.toggleFavorite(id, !current) }
    }

    fun deleteRecipe(id: Long) {
        viewModelScope.launch { repository.deleteRecipe(id) }
    }

    fun exportBook(context: Context) {
        viewModelScope.launch {
            val book = repository.getRecipeBookOnce(bookId) ?: return@launch
            val recipes = repository.getRecipesForBookOnce(bookId)
            RecipeExporter.shareBook(context, book, recipes)
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
        return recipes.applyFilter(filter)
            .groupBy { it.categoryName ?: UNCATEGORIZED_CATEGORY_LABEL }
            .toSortedMap(compareBy { if (it == UNCATEGORIZED_CATEGORY_LABEL) "￿" else it.lowercase() })
            .map { (category, recipesInGroup) -> RecipeGroup(category, recipesInGroup.map { it.toSummary() }) }
    }
}
