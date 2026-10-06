package org.calamares.miga.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.remote.CatalogFetchResult
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.data.remote.PacksCatalogClient
import org.calamares.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * [installedVersion] is the locally installed version of this pack (see RecipeBook.packVersion).
 */
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
                        .mapNotNull { book -> book.packId?.let { it to (book.packVersion ?: 0) } }
                        .toMap()
                    val items = result.packs.map { entry -> PackListItem(entry, installedVersionByPackId[entry.id]) }
                    _uiState.value = CatalogUiState.Loaded(items)
                }
            }
        }
    }
}
