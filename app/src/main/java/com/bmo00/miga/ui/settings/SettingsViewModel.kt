package com.bmo00.miga.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.export.RecipeExportDto
import com.bmo00.miga.data.export.RecipeExporter
import com.bmo00.miga.data.export.toDraft
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.data.model.UpdateChannel
import com.bmo00.miga.data.remote.UpdateChecker
import com.bmo00.miga.data.remote.UpdateInfo
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val info: UpdateInfo) : UpdateCheckState
}

class SettingsViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    val biometricLockEnabled: StateFlow<Boolean> = settingsRepository.observeBiometricLockEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLockEnabled(enabled) }
    }

    val autoCheckUpdatesEnabled: StateFlow<Boolean> = settingsRepository.observeAutoCheckUpdatesEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckUpdatesEnabled(enabled) }
    }

    val updateChannel: StateFlow<UpdateChannel> = settingsRepository.observeUpdateChannel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateChannel.STABLE)

    fun setUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch { settingsRepository.setUpdateChannel(channel) }
    }

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState

    fun checkForUpdatesNow() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            val info = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, updateChannel.value)
            _updateCheckState.value = if (info != null) UpdateCheckState.Available(info) else UpdateCheckState.UpToDate
        }
    }

    fun exportLibrary(context: Context, destination: Uri) {
        viewModelScope.launch {
            val recipes = repository.getAllRecipesOnce()
            RecipeExporter.exportLibrary(context, destination, recipes)
        }
    }

    fun importLibrary(context: Context, source: Uri, onFinished: (Int) -> Unit) {
        viewModelScope.launch {
            val count = RecipeExporter.importLibrary(context, source, repository)
            onFinished(count)
        }
    }

    val books: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun parseRecipeJson(context: Context, source: Uri): RecipeExportDto? =
        RecipeExporter.importRecipe(context, source)

    fun importRecipeIntoBook(dto: RecipeExportDto, bookId: Long, onFinished: () -> Unit) {
        viewModelScope.launch {
            repository.saveRecipe(dto.toDraft(bookId))
            onFinished()
        }
    }
}
