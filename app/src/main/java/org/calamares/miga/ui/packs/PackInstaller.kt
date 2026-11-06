package org.calamares.miga.ui.packs

import android.content.Context
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.PackImportResult
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.data.remote.PacksCatalogClient
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.remote.CatalogFetchResult
import org.calamares.miga.data.remote.visibleFor
import kotlinx.coroutines.flow.first

/** Downloads and installs packs, for the pack page and for several packs from the catalogue. */
object PackInstaller {

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
        entries: List<PackEntryDto>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<String> {
        val failed = mutableListOf<String>()
        entries.forEachIndexed { index, entry ->
            onProgress(index, entries.size)
            if (install(context, repository, entry) is PackImportResult.Error) failed += entry.name
        }
        return failed
    }

    /** Downloads [entry]'s ZIP and installs it, or updates the installed copy, with its series. */
    suspend fun install(context: Context, repository: RecipeRepository, entry: PackEntryDto): PackImportResult {
        val bytes = PacksCatalogClient.downloadPackZip(entry.downloadUrl)
            ?: return PackImportResult.Error(L10n.str(R.string.couldnt_download_pack_check_connection))
        return RecipeExporter.importPackFromBytes(context, bytes, repository, entry.id, entry.latestVersion, entry.series)
    }
}
