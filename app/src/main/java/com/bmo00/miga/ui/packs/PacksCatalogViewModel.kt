package com.bmo00.miga.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.remote.CatalogFetchResult
import com.bmo00.miga.data.remote.PackEntryDto
import com.bmo00.miga.data.remote.PacksCatalogClient
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** [installedVersion] es la versión ya instalada localmente de este pack (ver RecipeBook.packVersion). */
data class PackListItem(val entry: PackEntryDto, val installedVersion: Int?) {
    val isInstalled: Boolean get() = installedVersion != null
    val hasUpdate: Boolean get() = installedVersion != null && entry.latestVersion > installedVersion
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Loaded(val items: List<PackListItem>) : CatalogUiState
    data class Error(val reason: String) : CatalogUiState
}

class PacksCatalogViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CatalogUiState.Loading
            val repoPath = settingsRepository.observePacksCatalogRepo().first()
            when (val result = PacksCatalogClient.fetchCatalog(repoPath)) {
                is CatalogFetchResult.Error -> _uiState.value = CatalogUiState.Error(result.reason)
                is CatalogFetchResult.Success -> {
                    val installedVersionByPackId = repository.observeRecipeBooks().first()
                        .filter { it.isPack }
                        .associate { it.packId!! to (it.packVersion ?: 0) }
                    val items = result.packs.map { entry -> PackListItem(entry, installedVersionByPackId[entry.id]) }
                    _uiState.value = CatalogUiState.Loaded(items)
                }
            }
        }
    }
}
