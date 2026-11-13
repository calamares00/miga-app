package org.calamares.miga.ui.packs

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.export.PackImportResult
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.remote.CatalogFetchResult
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.remote.PackInstallStats
import org.calamares.miga.data.remote.PackStatsClient
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.data.remote.PacksCatalogClient
import org.calamares.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface PackDetailUiState {
    data object Loading : PackDetailUiState
    data class Loaded(val entry: PackEntryDto, val installedVersion: Int?) : PackDetailUiState
    data class Error(val reason: String) : PackDetailUiState
}

sealed interface InstallState {
    data object Idle : InstallState
    data object Installing : InstallState
    data class Error(val reason: String) : InstallState
}

class PackDetailViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
    private val packId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<PackDetailUiState>(PackDetailUiState.Loading)
    val uiState: StateFlow<PackDetailUiState> = _uiState

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    private val _installStats = MutableStateFlow<Map<String, PackInstallStats>>(emptyMap())
    /** Installs by family from the anonymous counter; only for the official catalogue. */
    val installStats: StateFlow<Map<String, PackInstallStats>> = _installStats

    init {
        viewModelScope.launch {
            val repoPath = settingsRepository.observePacksCatalogRepo().first()
            if (repoPath == DEFAULT_PACKS_CATALOG) {
                launch { PackStatsClient.fetchStats()?.let { _installStats.value = it } }
            }
            when (val result = PacksCatalogClient.fetchCatalog(repoPath)) {
                is CatalogFetchResult.Error -> _uiState.value = PackDetailUiState.Error(result.reason)
                is CatalogFetchResult.Success -> {
                    val entry = result.packs.find { it.id == packId }
                    _uiState.value = if (entry == null) {
                        PackDetailUiState.Error(L10n.str(R.string.pack_no_longer_catalogue))
                    } else {
                        repository.books.updatePackSeries(mapOf(entry.id to entry.series))
                        PackDetailUiState.Loaded(entry, repository.books.findRecipeBookByPackId(packId)?.packVersion)
                    }
                }
            }
        }
    }

    /**
     * Downloads and installs (or updates) this pack; on success [onInstalled] gets the pack name,
     * to go back to the catalogue and say it is installed.
     */
    fun install(context: Context, onInstalled: (String) -> Unit) {
        val state = _uiState.value as? PackDetailUiState.Loaded ?: return
        viewModelScope.launch {
            _installState.value = InstallState.Installing
            // In PackJobs: the install goes on if this page is closed or the app left.
            when (val result = PackJobs.install(context, repository, settingsRepository, state.entry).await()) {
                is PackImportResult.Success -> {
                    _installState.value = InstallState.Idle
                    onInstalled(state.entry.name)
                }
                is PackImportResult.Error -> _installState.value = InstallState.Error(result.reason)
            }
        }
    }

    /** Uninstalls this pack (its book and the recipes still in it); the page stays, to reinstall. */
    fun uninstall() {
        val state = _uiState.value as? PackDetailUiState.Loaded ?: return
        viewModelScope.launch {
            _installState.value = InstallState.Installing
            repository.books.uninstallPackById(packId)
            _uiState.value = state.copy(installedVersion = null)
            _installState.value = InstallState.Idle
        }
    }
}
