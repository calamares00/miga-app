package com.bmo00.miga.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.remote.UpdateCheckResult
import com.bmo00.miga.data.remote.UpdateChecker
import com.bmo00.miga.data.remote.UpdateInfo
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeBooksViewModel(
    repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val books: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val viewMode: StateFlow<RecipeListViewMode> = settingsRepository.observeRecipeBookListViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeListViewMode.GRID)

    fun setViewMode(mode: RecipeListViewMode) {
        viewModelScope.launch { settingsRepository.setRecipeBookListViewMode(mode) }
    }

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable

    init {
        viewModelScope.launch {
            if (settingsRepository.observeAutoCheckUpdatesEnabled().first()) {
                val channel = settingsRepository.observeUpdateChannel().first()
                val result = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, channel)
                _updateAvailable.value = (result as? UpdateCheckResult.UpdateFound)?.info
            }
        }
    }

    fun dismissUpdateBanner() {
        _updateAvailable.value = null
    }
}
