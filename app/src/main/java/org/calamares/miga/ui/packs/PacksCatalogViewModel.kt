package org.calamares.miga.ui.packs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.PackImportResult
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.remote.CatalogFetchResult
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.remote.PackInstallStats
import org.calamares.miga.data.remote.PackStatsClient
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.data.remote.PacksCatalogClient
import org.calamares.miga.data.remote.visibleFor
import org.calamares.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [installedVersion] is the locally installed version of this pack (see RecipeBook.packVersion).
 */
data class PackListItem(val entry: PackEntryDto, val installedVersion: Int?) {
    val isInstalled: Boolean get() = installedVersion != null
    val hasUpdate: Boolean get() = installedVersion != null && entry.latestVersion > installedVersion
    /** Not installed yet, or installed with a newer version available. */
    val canInstall: Boolean get() = !isInstalled || hasUpdate
}

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Loaded(val items: List<PackListItem>) : CatalogUiState
    data class Error(val reason: String) : CatalogUiState
}

/** Progress of installing or uninstalling several packs: [done] of [total]. */
/** Which packs the catalogue lists, by whether they are installed. */
enum class PackStatusFilter { ALL, INSTALLED, NOT_INSTALLED }

fun PackListItem.matches(filter: PackStatusFilter): Boolean = when (filter) {
    PackStatusFilter.ALL -> true
    PackStatusFilter.INSTALLED -> isInstalled
    PackStatusFilter.NOT_INSTALLED -> !isInstalled
}

data class BatchProgress(val done: Int, val total: Int, val uninstalling: Boolean)

private sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data class Loaded(val packs: List<PackEntryDto>) : CatalogLoad
    data class Error(val reason: String) : CatalogLoad
}

class PacksCatalogViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val catalog = MutableStateFlow<CatalogLoad>(CatalogLoad.Loading)

    /**
     * The catalogue joined with the installed books, so installing or uninstalling (here, on the
     * pack page or from the books list) shows at once without downloading the catalogue again.
     */
    val uiState: StateFlow<CatalogUiState> = combine(catalog, repository.books.observeRecipeBooks()) { load, books ->
        when (load) {
            is CatalogLoad.Loading -> CatalogUiState.Loading
            is CatalogLoad.Error -> CatalogUiState.Error(load.reason)
            is CatalogLoad.Loaded -> {
                val installedVersionByPackId = books
                    .mapNotNull { book -> book.packId?.let { it to (book.packVersion ?: 0) } }
                    .toMap()
                CatalogUiState.Loaded(
                    load.packs
                        .visibleFor(L10n.locale().language, installedVersionByPackId.keys)
                        .map { entry -> PackListItem(entry, installedVersionByPackId[entry.id]) }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogUiState.Loading)

    private val _collapsedSeries = MutableStateFlow<Set<String>>(emptySet())
    /** Series folded in the list ("" for packs without a series); kept while the app is open. */
    val collapsedSeries: StateFlow<Set<String>> = _collapsedSeries

    fun toggleSeries(series: String) {
        _collapsedSeries.value = _collapsedSeries.value.let { if (series in it) it - series else it + series }
    }

    fun setAllSeriesCollapsed(series: Collection<String>, collapsed: Boolean) {
        _collapsedSeries.value = if (collapsed) series.toSet() else emptySet()
    }

    private val _statusFilter = MutableStateFlow(PackStatusFilter.ALL)
    val statusFilter: StateFlow<PackStatusFilter> = _statusFilter

    fun setStatusFilter(filter: PackStatusFilter) {
        _statusFilter.value = filter
    }

    /** Ids of the packs selected to install or uninstall several at once; empty outside selection. */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    /** Whether the list is in selection mode (it stays on while the selection is emptied). */
    private val _selecting = MutableStateFlow(false)
    val selecting: StateFlow<Boolean> = _selecting

    private val _progress = MutableStateFlow<BatchProgress?>(null)
    val progress: StateFlow<BatchProgress?> = _progress

    /** Result of the last batch (what was done, or which packs failed), until dismissed. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _installStats = MutableStateFlow<Map<String, PackInstallStats>>(emptyMap())
    /** Installs by family from the anonymous counter; only for the official catalogue. */
    val installStats: StateFlow<Map<String, PackInstallStats>> = _installStats

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            catalog.value = CatalogLoad.Loading
            val repoPath = settingsRepository.observePacksCatalogRepo().first()
            if (repoPath == DEFAULT_PACKS_CATALOG) {
                launch { PackStatsClient.fetchStats()?.let { _installStats.value = it } }
            }
            when (val result = PacksCatalogClient.fetchCatalog(repoPath)) {
                is CatalogFetchResult.Error -> catalog.value = CatalogLoad.Error(result.reason)
                is CatalogFetchResult.Success -> {
                    // The user opened the catalogue, so its data may be used: packs installed before
                    // series existed get theirs for the books list.
                    repository.books.updatePackSeries(result.packs.associate { it.id to it.series })
                    catalog.value = CatalogLoad.Loaded(result.packs)
                }
            }
        }
    }

    fun startSelection(packId: String? = null) {
        _selecting.value = true
        if (packId != null) _selected.value = _selected.value + packId
    }

    fun toggleSelection(packId: String) {
        _selected.value = if (packId in _selected.value) _selected.value - packId else _selected.value + packId
    }

    fun selectAll(packIds: Collection<String>) {
        _selected.value = _selected.value + packIds
    }

    fun clearSelection() {
        _selecting.value = false
        _selected.value = emptySet()
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun selectedItems(): List<PackListItem> =
        (uiState.value as? CatalogUiState.Loaded)?.items.orEmpty().filter { it.entry.id in _selected.value }

    /** Installs the selected packs that are not installed and updates those with a new version. */
    fun installSelected(context: Context) {
        val items = selectedItems().filter { it.canInstall }
        if (items.isEmpty() || _progress.value != null) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            val failed = mutableListOf<String>()
            items.forEachIndexed { index, item ->
                _progress.value = BatchProgress(index, items.size, uninstalling = false)
                val result = PackInstaller.install(appContext, repository, settingsRepository, item.entry)
                if (result is PackImportResult.Error) failed += item.entry.name
            }
            _progress.value = null
            clearSelection()
            _message.value = if (failed.isEmpty()) {
                L10n.str(R.string.x_packs_installed, items.size)
            } else {
                L10n.str(R.string.couldnt_install_packs_x, failed.joinToString(", "))
            }
        }
    }

    /** Updates every installed pack that has a newer version. */
    fun updateAll(context: Context) {
        val entries = (uiState.value as? CatalogUiState.Loaded)?.items.orEmpty().filter { it.hasUpdate }.map { it.entry }
        if (entries.isEmpty() || _progress.value != null) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            val failed = PackInstaller.installAll(appContext, repository, settingsRepository, entries) { done, total ->
                _progress.value = BatchProgress(done, total, uninstalling = false)
            }
            _progress.value = null
            _message.value = if (failed.isEmpty()) {
                L10n.str(R.string.x_packs_updated, entries.size)
            } else {
                L10n.str(R.string.couldnt_install_packs_x, failed.joinToString(", "))
            }
        }
    }

    /** Uninstalls the selected packs that are installed. */
    fun uninstallSelected() {
        val items = selectedItems().filter { it.isInstalled }
        if (items.isEmpty() || _progress.value != null) return
        viewModelScope.launch {
            items.forEachIndexed { index, item ->
                _progress.value = BatchProgress(index, items.size, uninstalling = true)
                repository.books.uninstallPackById(item.entry.id)
            }
            _progress.value = null
            clearSelection()
            _message.value = L10n.str(R.string.x_packs_uninstalled, items.size)
        }
    }
}
