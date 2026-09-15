package com.bmo00.miga.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.health.RecipeHealthResult
import com.bmo00.miga.data.health.healthClientFor
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.nutrition.RecipeNutritionResult
import com.bmo00.miga.data.nutrition.nutritionClientFor
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.data.substitution.IngredientSubstitution
import com.bmo00.miga.data.substitution.SubstitutionResult
import com.bmo00.miga.data.substitution.substitutionClientFor
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

/** Estado del diálogo de "sustituir ingrediente" (ver RecipeDetailScreen). A diferencia de
 *  [HealthState]/[NutritionState], no se cachea nada - cada [ingredientName] dispara una consulta
 *  nueva sin guardar de un análisis anterior. */
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

    /** Si hay Gemini configurado y no hay ya una valoración vigente, la analiza y la cachea. */
    fun fetchHealthinessIfNeeded() {
        if (healthCheckStarted) return
        healthCheckStarted = true
        viewModelScope.launch {
            val current = recipe.filterNotNull().first()
            val provider = settingsRepository.observeVisionProvider().first()
            val apiKey = settingsRepository.apiKeyFor(provider)
            if (apiKey.isBlank()) {
                _healthState.value = HealthState.NotConfigured
                return@launch
            }
            if (current.healthRating != null) {
                _healthState.value = HealthState.Loaded
                return@launch
            }
            _healthState.value = HealthState.Loading
            val model = settingsRepository.modelFor(provider)
            val ingredientsText = current.ingredientGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.ingredients.joinToString("\n") { "- ${formatIngredient(it, 1.0)}" }
            }
            val stepsText = current.stepGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.instructions.joinToString("\n") { "- $it" }
            }
            when (val result = healthClientFor(provider).analyzeHealthiness(ingredientsText, stepsText, apiKey, model)) {
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

    /** Si hay un proveedor de IA configurado y no hay ya una estimación vigente, la analiza y la cachea. */
    fun fetchNutritionIfNeeded() {
        if (nutritionCheckStarted) return
        nutritionCheckStarted = true
        viewModelScope.launch {
            val current = recipe.filterNotNull().first()
            val provider = settingsRepository.observeVisionProvider().first()
            val apiKey = settingsRepository.apiKeyFor(provider)
            if (apiKey.isBlank()) {
                _nutritionState.value = NutritionState.NotConfigured
                return@launch
            }
            if (current.nutritionInfo != null) {
                _nutritionState.value = NutritionState.Loaded
                return@launch
            }
            _nutritionState.value = NutritionState.Loading
            val model = settingsRepository.modelFor(provider)
            val ingredientsText = current.ingredientGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.ingredients.joinToString("\n") { "- ${formatIngredient(it, 1.0)}" }
            }
            val stepsText = current.stepGroups.joinToString("\n") { group ->
                val header = group.name?.let { "$it:\n" }.orEmpty()
                header + group.instructions.joinToString("\n") { "- $it" }
            }
            when (
                val result = nutritionClientFor(provider)
                    .analyzeNutrition(ingredientsText, stepsText, current.servings, apiKey, model)
            ) {
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

    /** Consulta sustitutos con IA para [ingredientName], sin caché - cada llamada es una consulta
     *  nueva. Abre el diálogo de resultado (ver RecipeDetailScreen). */
    fun findSubstitutesFor(ingredientName: String) {
        viewModelScope.launch {
            _substitutionDialogState.value = SubstitutionDialogState.Loading(ingredientName)
            val current = recipe.filterNotNull().first()
            val provider = settingsRepository.observeVisionProvider().first()
            val apiKey = settingsRepository.apiKeyFor(provider)
            if (apiKey.isBlank()) {
                _substitutionDialogState.value = SubstitutionDialogState.NotConfigured(ingredientName)
                return@launch
            }
            val model = settingsRepository.modelFor(provider)
            when (val result = substitutionClientFor(provider).suggestSubstitutes(ingredientName, current.name, apiKey, model)) {
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
