package org.calamares.miga.ui.dishsearch

import org.calamares.miga.data.ai.runAi
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.search.DishSearchResult
import org.calamares.miga.data.search.DishSuggestion
import org.calamares.miga.data.search.dishSearchClientFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface DishSearchUiState {
    data object Idle : DishSearchUiState
    data object Loading : DishSearchUiState
    data class Loaded(val dishes: List<DishSuggestion>) : DishSearchUiState
    data object NotConfigured : DishSearchUiState
    data class Error(val reason: String) : DishSearchUiState
}

/** Sugiere platos con IA a partir de una petición libre (zona/país, tipo de plato, ingrediente...);
 *  la generación de la receta completa del plato elegido ocurre después, ya en el editor (ver
 *  RecipeEditorViewModel.startDishGeneration), reutilizando su mecanismo de precarga. */
class DishSearchViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow<DishSearchUiState>(DishSearchUiState.Idle)
    val state: StateFlow<DishSearchUiState> = _state

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _state.value = DishSearchUiState.Loading
            val result = settingsRepository.runAi<DishSearchResult>(
                errorOf = { (it as? DishSearchResult.Error)?.reason },
                error = { DishSearchResult.Error(it) }
            ) { ai -> dishSearchClientFor(ai.provider).searchDishes(trimmed, ai.apiKey, ai.model) }
            _state.value = when (result) {
                null -> DishSearchUiState.NotConfigured
                is DishSearchResult.Success ->
                    if (result.dishes.isEmpty()) DishSearchUiState.Error(L10n.str(R.string.no_han_encontrado_platos_esa))
                    else DishSearchUiState.Loaded(result.dishes)
                is DishSearchResult.Error -> DishSearchUiState.Error(result.reason)
            }
        }
    }
}
