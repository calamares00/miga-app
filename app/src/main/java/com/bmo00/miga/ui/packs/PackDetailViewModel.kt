package com.bmo00.miga.ui.packs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.export.PackImportResult
import com.bmo00.miga.data.export.RecipeExporter
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.remote.CatalogFetchResult
import com.bmo00.miga.data.remote.PackEntryDto
import com.bmo00.miga.data.remote.PacksCatalogClient
import com.bmo00.miga.data.repository.RecipeRepository
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
                        PackDetailUiState.Error("Este pack ya no está en el catálogo")
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
                _installState.value = InstallState.Error("No se pudo descargar el pack. Comprueba tu conexión e inténtalo de nuevo.")
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
