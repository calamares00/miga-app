package com.bmo00.miga.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.model.RecipeBookSummary
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

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable

    init {
        viewModelScope.launch {
            if (settingsRepository.observeAutoCheckUpdatesEnabled().first()) {
                _updateAvailable.value = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
            }
        }
    }

    fun dismissUpdateBanner() {
        _updateAvailable.value = null
    }
}
