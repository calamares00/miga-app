package org.calamares.miga.ui.tips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.tips.CookingTip

/**
 * The tips screen: the tips imported by the user (found by the assistant, newest first) and the
 * [builtIn] ones bundled with the app, which ones are saved and the tip-of-the-day switch.
 */
class TipsViewModel(
    private val settingsRepository: SettingsRepository,
    private val builtIn: List<CookingTip>
) : ViewModel() {

    val tips: StateFlow<List<CookingTip>> = settingsRepository.observeImportedTips()
        .map { imported -> imported.map { it.toCookingTip() } + builtIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), builtIn)

    val savedIds: StateFlow<Set<String>> = settingsRepository.observeSavedTips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val tipOfTheDayEnabled: StateFlow<Boolean> = settingsRepository.observeTipOfTheDayEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSaved(id: String, saved: Boolean) {
        viewModelScope.launch { settingsRepository.setTipSaved(id, saved) }
    }

    /** Deletes an imported tip (bundled ones cannot be). */
    fun deleteImported(id: String) {
        viewModelScope.launch { settingsRepository.removeImportedTip(id) }
    }

    fun setTipOfTheDayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTipOfTheDayEnabled(enabled) }
    }
}
