package org.calamares.miga.ui.list

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.export.RecipeImportResult
import org.calamares.miga.data.export.toDraft
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.local.entity.CategoryEntity
import org.calamares.miga.data.local.entity.TagEntity
import org.calamares.miga.data.local.entity.UtensilEntity
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipeDraft
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.formatIngredientText
import org.calamares.miga.data.model.toDraft
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.ai.aiCandidates
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.data.health.RecipeHealthResult
import org.calamares.miga.data.health.analyzeHealthiness
import org.calamares.miga.data.nutrition.RecipeNutritionResult
import org.calamares.miga.data.nutrition.analyzeNutrition
import org.calamares.miga.data.model.RecipeFilter
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.data.model.RecipeSummary
import org.calamares.miga.data.model.UNCATEGORIZED_CATEGORY_LABEL
import org.calamares.miga.data.model.applyFilter
import org.calamares.miga.data.model.toSummary
import org.calamares.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val availableIngredients: List<String> = emptyList(),
    /**
     * Book installed from a pack (see RecipeBook.isPack): read-only, recipes cannot be added,
     * edited or deleted.
     */
    val isPackBook: Boolean = false
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
                RecipeExporter.shareRecipes(context, L10n.str(R.string.file_name_selected_recipes), book, recipes)
            }
            _selectedIds.value = emptySet()
        }
    }

    // --- Bulk editing of the selected recipes ---

    fun selectAll() {
        _selectedIds.value = uiState.value.groups.flatMap { group -> group.recipes.map { it.id } }.toSet()
    }

    /** Books recipes can be moved or copied to: all except this one and packs (read-only). */
    val targetBooks: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .map { books -> books.filter { it.id != bookId && !it.isPack } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryNames: StateFlow<List<String>> = repository.observeCategories()
        .map { categories -> categories.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Whether "Recalculate health and nutrition" can be offered: AI on and at least one of the two
     * enabled.
     */
    val aiRecalculationAvailable: StateFlow<Boolean> = combine(
        settingsRepository.observeAiHealthEnabled(),
        settingsRepository.observeAiNutritionEnabled()
    ) { health, nutrition -> health || nutrition }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private suspend fun takeSelectedRecipes(): List<Recipe> {
        val ids = _selectedIds.value
        _selectedIds.value = emptySet()
        return repository.getRecipesForBookOnce(bookId).filter { it.id in ids }
    }

    /**
     * Applies [transform] to every selected recipe and saves it through the normal path (sync
     * included).
     */
    private fun updateSelected(onMessage: (String) -> Unit, transform: (RecipeDraft) -> RecipeDraft) {
        viewModelScope.launch {
            val recipes = takeSelectedRecipes()
            recipes.forEach { repository.saveRecipe(transform(it.toDraft())) }
            onMessage(L10n.str(R.string.bulk_updated_n, recipes.size))
        }
    }

    fun bulkSetCategory(categoryName: String?, onMessage: (String) -> Unit) =
        updateSelected(onMessage) { it.copy(categoryName = categoryName?.trim()?.takeIf { name -> name.isNotEmpty() }) }

    fun bulkSetDifficulty(difficulty: Difficulty, onMessage: (String) -> Unit) =
        updateSelected(onMessage) { it.copy(difficulty = difficulty) }

    /**
     * Changes the servings. With [scaleIngredients] the quantities are rescaled so the recipe still
     * adds up.
     */
    fun bulkSetServings(servings: Int, scaleIngredients: Boolean, onMessage: (String) -> Unit) =
        updateSelected(onMessage) { draft ->
            val factor = servings.toDouble() / draft.servings.coerceAtLeast(1)
            draft.copy(
                servings = servings,
                ingredientGroups = if (!scaleIngredients || factor == 1.0) draft.ingredientGroups else draft.ingredientGroups.map { group ->
                    group.copy(ingredients = group.ingredients.map { ingredient -> ingredient.copy(quantity = ingredient.quantity?.let { roundQuantity(it * factor) }) })
                }
            )
        }

    fun bulkSetSource(source: String, onMessage: (String) -> Unit) =
        updateSelected(onMessage) { it.copy(source = source.trim()) }

    fun bulkSetFavorite(favorite: Boolean, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val recipes = takeSelectedRecipes()
            recipes.forEach { repository.toggleFavorite(it.id, favorite) }
            onMessage(L10n.str(R.string.bulk_updated_n, recipes.size))
        }
    }

    fun bulkMoveTo(targetBookId: Long, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val recipes = takeSelectedRecipes()
            recipes.forEach { repository.moveRecipeToBook(it.id, targetBookId) }
            onMessage(L10n.str(R.string.bulk_moved_n, recipes.size))
        }
    }

    /**
     * Copies the recipes to another book as new recipes with their own photo copies, so deleting
     * one never leaves the other without a photo.
     */
    fun bulkCopyTo(context: Context, targetBookId: Long, onMessage: (String) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val recipes = takeSelectedRecipes()
            recipes.forEach { recipe ->
                val photos = withContext(Dispatchers.IO) {
                    recipe.photos.mapNotNull { photo ->
                        PhotoStorage.readBytes(photo.uri)
                            ?.let { bytes -> PhotoStorage.copyBytesToInternalStorage(appContext, bytes) }
                            ?.let { RecipePhoto(it, photo.isCover) }
                    }
                }
                repository.saveRecipe(recipe.toDraft().copy(id = 0L, uid = null, recipeBookId = targetBookId, photos = photos))
            }
            onMessage(L10n.str(R.string.bulk_copied_n, recipes.size))
        }
    }

    fun bulkAddToShoppingList(onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val recipes = takeSelectedRecipes()
            repository.addIngredientsToShoppingList(recipes.flatMap { recipe -> recipe.ingredientGroups.flatMap { it.ingredients } })
            onMessage(L10n.str(R.string.bulk_added_to_shopping_n, recipes.size))
        }
    }

    /**
     * Recalculates with AI the health rating and/or nutrition (as enabled in Settings) of the
     * selected recipes.
     */
    fun bulkRecalculateAi(onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val recipes = takeSelectedRecipes()
            if (settingsRepository.aiCandidates().isEmpty()) {
                onMessage(L10n.str(R.string.ai_no_provider))
                return@launch
            }
            val doHealth = settingsRepository.observeAiHealthEnabled().first()
            val doNutrition = settingsRepository.observeAiNutritionEnabled().first()
            onMessage(L10n.str(R.string.bulk_ai_started_n, recipes.size))
            var failed = 0
            AiKeepAlive.hold {
                recipes.forEach { recipe ->
                    val ingredientsText = recipe.ingredientGroups.joinToString("\n") { group ->
                        group.name?.let { "$it:\n" }.orEmpty() + group.ingredients.joinToString("\n") {
                            "- " + formatIngredientText(it.name, it.quantity, it.unit)
                        }
                    }
                    val stepsText = recipe.stepGroups.joinToString("\n") { group ->
                        group.name?.let { "$it:\n" }.orEmpty() + group.instructions.joinToString("\n") { "- $it" }
                    }
                    if (doHealth) {
                        val result = settingsRepository.runAi<RecipeHealthResult>(
                            errorOf = { (it as? RecipeHealthResult.Error)?.reason },
                            error = { RecipeHealthResult.Error(it) }
                        ) { ai -> ai.analyzeHealthiness(ingredientsText, stepsText) }
                        if (result is RecipeHealthResult.Success) {
                            val fingerprint = repository.computeHealthFingerprint(recipe.ingredientGroups, recipe.stepGroups)
                            repository.saveHealthRating(recipe.id, result.colorLevel, result.description, fingerprint, System.currentTimeMillis())
                        } else {
                            failed++
                        }
                    }
                    if (doNutrition) {
                        val result = settingsRepository.runAi<RecipeNutritionResult>(
                            errorOf = { (it as? RecipeNutritionResult.Error)?.reason },
                            error = { RecipeNutritionResult.Error(it) }
                        ) { ai -> ai.analyzeNutrition(ingredientsText, stepsText, recipe.servings) }
                        if (result is RecipeNutritionResult.Success) {
                            val fingerprint = repository.computeNutritionFingerprint(recipe.ingredientGroups, recipe.stepGroups)
                            repository.saveNutritionInfo(
                                recipe.id, result.caloriesPerServing, result.proteinGrams, result.carbsGrams, result.fatGrams,
                                fingerprint, System.currentTimeMillis()
                            )
                        } else {
                            failed++
                        }
                    }
                }
            }
            onMessage(
                if (failed == 0) L10n.str(R.string.bulk_ai_done_n, recipes.size)
                else L10n.str(R.string.bulk_ai_done_with_errors, recipes.size, failed)
            )
        }
    }

    fun importRecipeFile(context: Context, uri: Uri, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = RecipeExporter.importRecipe(context, uri)) {
                is RecipeImportResult.Success -> {
                    val recipeId = repository.saveRecipe(result.recipe.toDraft(bookId, result.photos))
                    RecipeExporter.applyHealthFromImport(repository, recipeId, result.recipe.health)
                    if (result.recipe.rating != null) repository.setRating(recipeId, result.recipe.rating)
                    onMessage(L10n.str(R.string.recipe_imported))
                }
                is RecipeImportResult.Error -> onMessage(L10n.str(R.string.couldnt_import_x, result.reason))
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
            availableIngredients = data.options.ingredientNames,
            isPackBook = book?.isPack == true
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

/** Rounds a rescaled quantity to something sensible for a recipe (at most 2 decimals). */
internal fun roundQuantity(value: Double): Double = kotlin.math.round(value * 100) / 100
