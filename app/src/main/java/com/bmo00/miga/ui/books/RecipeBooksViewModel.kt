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

data class ChangelogAnnouncement(val versionName: String, val entries: List<String>)

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

    private val _changelogAnnouncement = MutableStateFlow<ChangelogAnnouncement?>(null)
    val changelogAnnouncement: StateFlow<ChangelogAnnouncement?> = _changelogAnnouncement

    init {
        viewModelScope.launch {
            if (settingsRepository.observeAutoCheckUpdatesEnabled().first()) {
                val channel = settingsRepository.observeUpdateChannel().first()
                val result = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, channel)
                _updateAvailable.value = (result as? UpdateCheckResult.UpdateFound)?.info
            }
        }
        viewModelScope.launch {
            val lastSeen = settingsRepository.observeLastSeenVersionCode().first()
            val current = BuildConfig.VERSION_CODE
            if (lastSeen == 0) {
                // Primera vez que corre esta lógica (instalación nueva, o actualización desde una
                // versión anterior a que existiera el changelog en la app): no hay nada que
                // mostrar todavía, solo se empieza a registrar la versión vista a partir de ahora.
                settingsRepository.setLastSeenVersionCode(current)
            } else if (lastSeen < current) {
                val entries = (lastSeen + 1..current).flatMap { code ->
                    settingsRepository.readChangelog(code)
                        ?.lines()
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }
                if (entries.isNotEmpty()) {
                    _changelogAnnouncement.value = ChangelogAnnouncement(BuildConfig.VERSION_NAME, entries)
                }
                settingsRepository.setLastSeenVersionCode(current)
            }
        }
    }

    fun dismissUpdateBanner() {
        _updateAvailable.value = null
    }

    fun dismissChangelogAnnouncement() {
        _changelogAnnouncement.value = null
    }
}
