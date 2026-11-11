package org.calamares.miga.ui.packs

import android.content.Context
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.PackImportResult
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.remote.PackDownloadResult
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.data.remote.PacksCatalogClient
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.remote.CatalogFetchResult
import org.calamares.miga.data.remote.visibleFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.remote.PackStatsClient

/** Downloads and installs packs, for the pack page and for several packs from the catalogue. */
object PackInstaller {

    /** Install reports outlive the screen that installed the pack and never delay it. */
    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The installed packs that have a newer version in the catalogue, in the app language's list
     * (see visibleFor). Null when the catalogue cannot be read (offline, catalogue down).
     */
    suspend fun findUpdates(repository: RecipeRepository, settingsRepository: SettingsRepository): List<PackEntryDto>? {
        val installed = repository.getAllRecipeBooksOnce()
            .mapNotNull { book -> book.packId?.let { it to (book.packVersion ?: 0) } }
            .toMap()
        if (installed.isEmpty()) return emptyList()
        val result = PacksCatalogClient.fetchCatalog(settingsRepository.observePacksCatalogRepo().first())
        val packs = (result as? CatalogFetchResult.Success)?.packs ?: return null
        return packs.visibleFor(L10n.locale().language, installed.keys)
            .filter { entry -> installed[entry.id]?.let { entry.latestVersion > it } == true }
    }

    /** Installs [entries] one after another; returns the names of those that failed. */
    suspend fun installAll(
        context: Context,
        repository: RecipeRepository,
        settingsRepository: SettingsRepository,
        entries: List<PackEntryDto>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<String> {
        val failed = mutableListOf<String>()
        entries.forEachIndexed { index, entry ->
            onProgress(index, entries.size)
            if (install(context, repository, settingsRepository, entry) is PackImportResult.Error) failed += entry.name
        }
        return failed
    }

    /**
     * Downloads [entry]'s ZIP and installs it, or updates the installed copy, with its series. A new
     * install (not an update) from the official catalogue adds one to the pack's anonymous counter,
     * unless the user turned that off.
     */
    suspend fun install(
        context: Context,
        repository: RecipeRepository,
        settingsRepository: SettingsRepository,
        entry: PackEntryDto
    ): PackImportResult {
        val isNewInstall = repository.findRecipeBookByPackId(entry.id) == null
        val bytes = when (val download = PacksCatalogClient.downloadPackZip(entry.downloadUrl, entry.sha256)) {
            is PackDownloadResult.Success -> download.bytes
            PackDownloadResult.Failed -> return PackImportResult.Error(L10n.str(R.string.couldnt_download_pack_check_connection))
            PackDownloadResult.ChecksumMismatch -> return PackImportResult.Error(L10n.str(R.string.pack_checksum_mismatch))
            PackDownloadResult.NotHttps -> return PackImportResult.Error(L10n.str(R.string.catalogue_needs_https))
        }
        val result = RecipeExporter.importPackFromBytes(context, bytes, repository, entry.id, entry.latestVersion, entry.series)
        if (result is PackImportResult.Success && isNewInstall) {
            reportScope.launch {
                val official = settingsRepository.observePacksCatalogRepo().first() == DEFAULT_PACKS_CATALOG
                if (official && settingsRepository.observePackInstallStats().first()) PackStatsClient.reportInstall(entry.id)
            }
        }
        return result
    }
}
