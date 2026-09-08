package com.bmo00.miga.ui.books

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.crash.CrashReporter
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.remote.UpdateCheckResult
import com.bmo00.miga.data.remote.UpdateChecker
import com.bmo00.miga.data.remote.UpdateInfo
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.data.sync.SyncEngine
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
    private var autoSyncStarted = false

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

    /** Informe del último fallo no capturado (ver CrashReporter), si lo hay; solo local, nada se envía. */
    private val _crashReport = MutableStateFlow(CrashReporter.pendingReport())
    val crashReport: StateFlow<String?> = _crashReport

    fun dismissCrashReport() {
        CrashReporter.dismiss()
        _crashReport.value = null
    }

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

    /** Sincroniza automáticamente todas las conexiones configuradas (ver Ajustes → Servidor de
     *  sincronización) al abrir la app, mismo sitio y espíritu que el chequeo de actualizaciones de
     *  arriba - así los cambios de otras apps Miga conectadas al mismo namespace llegan sin que el
     *  usuario tenga que sincronizar a mano. Solo una vez por instancia de este ViewModel (que
     *  persiste mientras la pestaña de libros siga viva) para no repetir el sync en cada
     *  recomposición o cambio de pestaña. */
    fun syncAllOnOpen(context: Context) {
        if (autoSyncStarted) return
        autoSyncStarted = true
        viewModelScope.launch {
            repository.observeSyncConnections().first().forEach { connection ->
                syncEngine.syncConnection(context, connection.id)
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
