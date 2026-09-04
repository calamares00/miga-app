package org.calamares.miga.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.remote.UpdateCheckResult
import org.calamares.miga.data.remote.UpdateChecker
import org.calamares.miga.data.remote.UpdateInfo
import org.calamares.miga.data.repository.RecipeRepository
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
