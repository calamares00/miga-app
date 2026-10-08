package org.calamares.miga.ui.books

import kotlinx.coroutines.flow.combine
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.BuildConfig
import org.calamares.miga.crash.CrashReporter
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChangelogAnnouncement(val versionName: String, val entries: List<String>)

class RecipeBooksViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val syncEngine = SyncEngine(repository)

    /** True when AI is switched on and at least one provider is configured, so Ideas can be offered. */
    val aiReady: StateFlow<Boolean> = combine(
        settingsRepository.observeAiEnabled(),
        settingsRepository.observeGeminiApiKey(),
        settingsRepository.observeAnthropicApiKey(),
        settingsRepository.observeOpenRouterApiKey(),
        settingsRepository.observeOpenRouterModel()
    ) { enabled, gemini, anthropic, openRouter, openRouterModel ->
        enabled && (gemini.isNotBlank() || anthropic.isNotBlank() || (openRouter.isNotBlank() && openRouterModel.isNotBlank()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private var autoSyncStarted = false

    val books: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val viewMode: StateFlow<RecipeListViewMode> = settingsRepository.observeRecipeBookListViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeListViewMode.GRID)

    fun togglePinned(book: RecipeBookSummary) {
        viewModelScope.launch { repository.setRecipeBookPinned(book.id, !book.isPinned) }
    }

    fun setViewMode(mode: RecipeListViewMode) {
        viewModelScope.launch { settingsRepository.setRecipeBookListViewMode(mode) }
    }

    private val _changelogAnnouncement = MutableStateFlow<ChangelogAnnouncement?>(null)
    val changelogAnnouncement: StateFlow<ChangelogAnnouncement?> = _changelogAnnouncement

    /**
     * Report of the last uncaught crash (see CrashReporter), if any. Local only; nothing is sent.
     */
    private val _crashReport = MutableStateFlow(CrashReporter.pendingReport())
    val crashReport: StateFlow<String?> = _crashReport

    fun dismissCrashReport() {
        CrashReporter.dismiss()
        _crashReport.value = null
    }

    init {
        viewModelScope.launch {
            val lastSeen = settingsRepository.observeLastSeenVersionCode().first()
            val current = BuildConfig.VERSION_CODE
            if (lastSeen == 0) {
                // First run of this logic (fresh install, or update from a version without the
                // in-app changelog): nothing to show yet, just start recording the last seen
                // version.
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

    /**
     * Syncs every configured connection when the app opens, so changes from other devices on the
     * same namespace arrive without a manual sync. Runs once per ViewModel instance (which lives as
     * long as the books tab) to avoid syncing on every recomposition or tab switch.
     */
    fun syncAllOnOpen(context: Context) {
        if (autoSyncStarted) return
        autoSyncStarted = true
        viewModelScope.launch {
            repository.observeSyncConnections().first().forEach { connection ->
                syncEngine.syncConnection(context, connection.id)
            }
        }
    }

    fun dismissChangelogAnnouncement() {
        _changelogAnnouncement.value = null
    }
}
