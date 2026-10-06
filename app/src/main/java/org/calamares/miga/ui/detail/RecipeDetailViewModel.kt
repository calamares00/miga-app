package org.calamares.miga.ui.detail

import org.calamares.miga.data.ai.aiCandidates
import org.calamares.miga.data.ai.runAi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.health.RecipeHealthResult
import org.calamares.miga.data.health.analyzeHealthiness
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.nutrition.RecipeNutritionResult
import org.calamares.miga.data.nutrition.analyzeNutrition
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.substitution.IngredientSubstitution
import org.calamares.miga.data.substitution.SubstitutionResult
import org.calamares.miga.data.substitution.suggestSubstitutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HealthState {
    data object Idle : HealthState
    data object Loading : HealthState
    data object Loaded : HealthState
    data object NotConfigured : HealthState
    data class Error(val reason: String) : HealthState
}

sealed interface NutritionState {
    data object Idle : NutritionState
    data object Loading : NutritionState
    data object Loaded : NutritionState
    data object NotConfigured : NutritionState
    data class Error(val reason: String) : NutritionState
}

/**
 * State of the "substitute ingredient" dialog (see RecipeDetailScreen). Unlike [HealthState] and
 * [NutritionState] nothing is cached: every [ingredientName] triggers a new request.
 */
sealed interface SubstitutionDialogState {
    data object Hidden : SubstitutionDialogState
    data class Loading(val ingredientName: String) : SubstitutionDialogState
    data class Loaded(val ingredientName: String, val substitutions: List<IngredientSubstitution>) : SubstitutionDialogState
    data class NotConfigured(val ingredientName: String) : SubstitutionDialogState
    data class Error(val ingredientName: String, val reason: String) : SubstitutionDialogState
}

class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val recipeId: Long,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val recipe: StateFlow<Recipe?> = repository.observeRecipe(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ttsVoiceName: StateFlow<String?> = settingsRepository.observeTtsVoiceName()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recipeBooks: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _healthState = MutableStateFlow<HealthState>(HealthState.Idle)
    val healthState: StateFlow<HealthState> = _healthState
    private var healthCheckStarted = false

    /**
     * When AI is available and there is no valid rating yet, analyses the recipe and caches the
     * result.
     */
    fun fetchHealthinessIfNeeded() {
        if (healthCheckStarted) return
        healthCheckStarted = true
        viewModelScope.launch {
            // Turned off in Settings (or AI is off entirely): nothing is analysed or shown.
            if (!settingsRepository.observeAiHealthEnabled().first()) {
                _healthState.value = HealthState.Idle
                return@launch
            }
            val current = recipe.filterNotNull().first()
            if (settingsRepository.aiCandidates().isEmpty()) {
                _healthState.value = HealthState.NotConfigured
                return@launch
            }
            if (current.healthRating != null) {
                _healthState.value = HealthState.Loaded
                return@launch
            }
            _healthState.value = HealthState.Loading
            val ingredientsText = current.ingredientGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.ingredients.joinToString("\n") { "- ${formatIngredient(it, 1.0)}" }
            }
            val stepsText = current.stepGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.instructions.joinToString("\n") { "- $it" }
            }
            val result = settingsRepository.runAi<RecipeHealthResult>(
                errorOf = { (it as? RecipeHealthResult.Error)?.reason },
                error = { RecipeHealthResult.Error(it) }
            ) { ai -> ai.analyzeHealthiness(ingredientsText, stepsText) }
            when (result) {
                null -> _healthState.value = HealthState.NotConfigured
                is RecipeHealthResult.Success -> {
                    val fingerprint = repository.computeHealthFingerprint(current.ingredientGroups, current.stepGroups)
                    repository.saveHealthRating(current.id, result.colorLevel, result.description, fingerprint, System.currentTimeMillis())
                    _healthState.value = HealthState.Loaded
                }
                is RecipeHealthResult.Error -> _healthState.value = HealthState.Error(result.reason)
            }
        }
    }

    fun retryHealthCheck() {
        healthCheckStarted = false
        fetchHealthinessIfNeeded()
    }

    private val _nutritionState = MutableStateFlow<NutritionState>(NutritionState.Idle)
    val nutritionState: StateFlow<NutritionState> = _nutritionState
    private var nutritionCheckStarted = false

    /**
     * When AI is available and there is no valid estimate yet, analyses the recipe and caches the
     * result.
     */
    fun fetchNutritionIfNeeded() {
        if (nutritionCheckStarted) return
        nutritionCheckStarted = true
        viewModelScope.launch {
            if (!settingsRepository.observeAiNutritionEnabled().first()) {
                _nutritionState.value = NutritionState.Idle
                return@launch
            }
            val current = recipe.filterNotNull().first()
            if (settingsRepository.aiCandidates().isEmpty()) {
                _nutritionState.value = NutritionState.NotConfigured
                return@launch
            }
            if (current.nutritionInfo != null) {
                _nutritionState.value = NutritionState.Loaded
                return@launch
            }
            _nutritionState.value = NutritionState.Loading
            val ingredientsText = current.ingredientGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.ingredients.joinToString("\n") { "- ${formatIngredient(it, 1.0)}" }
            }
            val stepsText = current.stepGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.instructions.joinToString("\n") { "- $it" }
            }
            val result = settingsRepository.runAi<RecipeNutritionResult>(
                errorOf = { (it as? RecipeNutritionResult.Error)?.reason },
                error = { RecipeNutritionResult.Error(it) }
            ) { ai -> ai.analyzeNutrition(ingredientsText, stepsText, current.servings) }
            when (result) {
                null -> _nutritionState.value = NutritionState.NotConfigured
                is RecipeNutritionResult.Success -> {
                    val fingerprint = repository.computeNutritionFingerprint(current.ingredientGroups, current.stepGroups)
                    repository.saveNutritionInfo(
                        current.id, result.caloriesPerServing, result.proteinGrams, result.carbsGrams, result.fatGrams,
                        fingerprint, System.currentTimeMillis()
                    )
                    _nutritionState.value = NutritionState.Loaded
                }
                is RecipeNutritionResult.Error -> _nutritionState.value = NutritionState.Error(result.reason)
            }
        }
    }

    fun retryNutritionCheck() {
        nutritionCheckStarted = false
        fetchNutritionIfNeeded()
    }

    private val _substitutionDialogState = MutableStateFlow<SubstitutionDialogState>(SubstitutionDialogState.Hidden)
    val substitutionDialogState: StateFlow<SubstitutionDialogState> = _substitutionDialogState

    /**
     * Asks the AI for substitutes for [ingredientName], without caching. Opens the result dialog
     * (see RecipeDetailScreen).
     */
    fun findSubstitutesFor(ingredientName: String) {
        viewModelScope.launch {
            _substitutionDialogState.value = SubstitutionDialogState.Loading(ingredientName)
            val current = recipe.filterNotNull().first()
            val result = settingsRepository.runAi<SubstitutionResult>(
                errorOf = { (it as? SubstitutionResult.Error)?.reason },
                error = { SubstitutionResult.Error(it) }
            ) { ai -> ai.suggestSubstitutes(ingredientName, current.name) }
            when (result) {
                null -> _substitutionDialogState.value = SubstitutionDialogState.NotConfigured(ingredientName)
                is SubstitutionResult.Success ->
                    _substitutionDialogState.value = SubstitutionDialogState.Loaded(ingredientName, result.substitutions)
                is SubstitutionResult.Error ->
                    _substitutionDialogState.value = SubstitutionDialogState.Error(ingredientName, result.reason)
            }
        }
    }

    fun dismissSubstitutionDialog() {
        _substitutionDialogState.value = SubstitutionDialogState.Hidden
    }

    fun toggleFavorite() {
        val current = recipe.value ?: return
        viewModelScope.launch { repository.toggleFavorite(current.id, !current.isFavorite) }
    }

    /** Tapping the star that is already set clears the rating. */
    fun setRating(stars: Int) {
        val current = recipe.value ?: return
        val newRating = if (current.rating == stars) null else stars
        viewModelScope.launch { repository.setRating(current.id, newRating) }
    }

    fun markCooked() {
        viewModelScope.launch { repository.markCooked(recipeId) }
    }

    fun moveToBook(newBookId: Long) {
        viewModelScope.launch { repository.moveRecipeToBook(recipeId, newBookId) }
    }

    fun addIngredientsToShoppingList() {
        val current = recipe.value ?: return
        viewModelScope.launch { repository.addIngredientsToShoppingList(current.ingredientGroups.flatMap { it.ingredients }) }
    }

    fun deleteRecipe(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRecipe(recipeId)
            onDeleted()
        }
    }
}
