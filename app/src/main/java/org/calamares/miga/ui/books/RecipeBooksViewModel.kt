package org.calamares.miga.ui.books

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
    private var autoSyncStarted = false

    val books: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val viewMode: StateFlow<RecipeListViewMode> = settingsRepository.observeRecipeBookListViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeListViewMode.GRID)

    fun setViewMode(mode: RecipeListViewMode) {
        viewModelScope.launch { settingsRepository.setRecipeBookListViewMode(mode) }
    }

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
     *  sincronización) al abrir la app, en el mismo sitio que se hacía la comprobación inicial de
     *  arriba (versión e historial) - así los cambios de otras apps Miga conectadas al mismo namespace llegan sin que el
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

    fun dismissChangelogAnnouncement() {
        _changelogAnnouncement.value = null
    }
}
