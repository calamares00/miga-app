package com.bmo00.miga.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.health.RecipeHealthResult
import com.bmo00.miga.data.health.healthClientFor
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.repository.RecipeRepository
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
            val apiKey = settingsRepository.observeGeminiApiKey().first()
            if (apiKey.isBlank()) {
                _healthState.value = HealthState.NotConfigured
                return@launch
            }
            if (current.healthRating != null) {
                _healthState.value = HealthState.Loaded
                return@launch
            }
            _healthState.value = HealthState.Loading
            val provider = settingsRepository.observeVisionProvider().first()
            val model = settingsRepository.observeGeminiModel().first()
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

    fun deleteRecipe(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRecipe(recipeId)
            onDeleted()
        }
    }
}
