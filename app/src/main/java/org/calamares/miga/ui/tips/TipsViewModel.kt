package org.calamares.miga.ui.tips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.tips.CookingTip

/** The tips screen: the bundled [tips], which ones are saved and the tip-of-the-day switch. */
class TipsViewModel(
    private val settingsRepository: SettingsRepository,
    val tips: List<CookingTip>
) : ViewModel() {

    val savedIds: StateFlow<Set<String>> = settingsRepository.observeSavedTips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val tipOfTheDayEnabled: StateFlow<Boolean> = settingsRepository.observeTipOfTheDayEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSaved(id: String, saved: Boolean) {
        viewModelScope.launch { settingsRepository.setTipSaved(id, saved) }
    }

    fun setTipOfTheDayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTipOfTheDayEnabled(enabled) }
    }
}
