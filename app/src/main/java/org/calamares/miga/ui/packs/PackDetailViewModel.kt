package org.calamares.miga.ui.packs

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.export.PackImportResult
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.remote.CatalogFetchResult
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

    init {
        viewModelScope.launch {
            val repoPath = settingsRepository.observePacksCatalogRepo().first()
            when (val result = PacksCatalogClient.fetchCatalog(repoPath)) {
                is CatalogFetchResult.Error -> _uiState.value = PackDetailUiState.Error(result.reason)
                is CatalogFetchResult.Success -> {
                    val entry = result.packs.find { it.id == packId }
                    _uiState.value = if (entry == null) {
                        PackDetailUiState.Error(L10n.str(R.string.este_pack_ya_no_esta))
                    } else {
                        PackDetailUiState.Loaded(entry, repository.findRecipeBookByPackId(packId)?.packVersion)
                    }
                }
            }
        }
    }

    /** Descarga e instala (o actualiza) este pack; en éxito navega al libro instalado vía [onInstalled]. */
    fun install(context: Context, onInstalled: (Long) -> Unit) {
        val state = _uiState.value as? PackDetailUiState.Loaded ?: return
        viewModelScope.launch {
            _installState.value = InstallState.Installing
            val bytes = PacksCatalogClient.downloadPackZip(state.entry.downloadUrl)
            if (bytes == null) {
                _installState.value = InstallState.Error(L10n.str(R.string.no_pudo_descargar_pack_comprueba))
                return@launch
            }
            when (val result = RecipeExporter.importPackFromBytes(context, bytes, repository, packId, state.entry.latestVersion)) {
                is PackImportResult.Success -> {
                    _installState.value = InstallState.Idle
                    onInstalled(result.bookId)
                }
                is PackImportResult.Error -> _installState.value = InstallState.Error(result.reason)
            }
        }
    }
}
