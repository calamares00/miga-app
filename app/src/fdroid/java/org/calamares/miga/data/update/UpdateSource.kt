package org.calamares.miga.data.update

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.remote.AppInstallSource
import org.calamares.miga.data.remote.AppUpdateClient
import org.calamares.miga.ui.components.openWebLink

/**
 * New versions from the latest stable GitHub Release (never a prerelease), without any Google
 * library. For copies installed from GitHub or Obtainium. Copies installed by an F-Droid client
 * are left to it: F-Droid builds each version itself, some days after the Release, and updates
 * them on its own, so announcing the Release would send the user to a version not there yet.
 */
object UpdateSource {

    suspend fun check(context: Context): AvailableUpdate? {
        if (AppInstallSource.isFdroid(context)) return null
        val latest = AppUpdateClient.fetchLatest() ?: return null
        if (!AppUpdateClient.isNewer(latest.versionName, BuildConfig.VERSION_NAME)) return null
        return AvailableUpdate(key = latest.versionName, versionName = latest.versionName, pageUrl = latest.pageUrl)
    }

    /** What "Update" does: opens the Release page, where the APKs are. */
    @Composable
    fun rememberUpdateAction(): (AvailableUpdate) -> Unit {
        val context = LocalContext.current
        return remember(context) { { update -> update.pageUrl?.let { openWebLink(context, it) } } }
    }
}
