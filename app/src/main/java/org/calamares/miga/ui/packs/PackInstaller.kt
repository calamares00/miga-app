package org.calamares.miga.ui.packs

import android.content.Context
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.PackImportResult
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.data.remote.PacksCatalogClient
import org.calamares.miga.data.repository.RecipeRepository

/** Downloads and installs packs, for the pack page and for several packs from the catalogue. */
object PackInstaller {

    /** Downloads [entry]'s ZIP and installs it, or updates the installed copy, with its series. */
    suspend fun install(context: Context, repository: RecipeRepository, entry: PackEntryDto): PackImportResult {
        val bytes = PacksCatalogClient.downloadPackZip(entry.downloadUrl)
            ?: return PackImportResult.Error(L10n.str(R.string.couldnt_download_pack_check_connection))
        return RecipeExporter.importPackFromBytes(context, bytes, repository, entry.id, entry.latestVersion, entry.series)
    }
}
