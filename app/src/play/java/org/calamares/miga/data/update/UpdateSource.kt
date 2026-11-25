package org.calamares.miga.data.update

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.calamares.miga.data.remote.AppInstallSource

/**
 * New versions through Google Play's in-app updates. Play knows the user's track, so testers of
 * the closed beta hear about each beta and everyone else only about public versions. The update
 * is installed inside the app, on Play's own screen, which restarts Miga when it is done.
 */
object UpdateSource {

    private val immediate: AppUpdateOptions = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()

    /** The version Play offers this user, or null (none, not installed from Play, offline...). */
    suspend fun check(context: Context): AvailableUpdate? = check(AppUpdateManagerFactory.create(context))

    internal suspend fun check(manager: AppUpdateManager): AvailableUpdate? {
        val info = try {
            manager.requestAppUpdateInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        if (!info.canUpdate()) return null
        return AvailableUpdate(key = "code:${info.availableVersionCode()}", versionName = null)
    }

    private fun AppUpdateInfo.canUpdate() =
        updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

    /**
     * What "Update" does: Play's update screen, or Miga's page in the Play Store if Play no longer
     * offers it. An update left halfway (the app was closed during it) resumes when Miga is back.
     */
    @Composable
    fun rememberUpdateAction(): (AvailableUpdate) -> Unit {
        val context = LocalContext.current
        val manager = remember(context) { AppUpdateManagerFactory.create(context) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
        val scope = rememberCoroutineScope()
        LifecycleResumeEffect(manager) {
            val job = scope.launch {
                val info = runCatching { manager.requestAppUpdateInfo() }.getOrNull()
                if (info?.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    manager.startUpdateFlowForResult(info, launcher, immediate)
                }
            }
            onPauseOrDispose { job.cancel() }
        }
        return remember(manager, launcher, scope) {
            { _ ->
                scope.launch {
                    val info = runCatching { manager.requestAppUpdateInfo() }.getOrNull()
                    if (info != null && info.canUpdate()) manager.startUpdateFlowForResult(info, launcher, immediate)
                    else AppInstallSource.openStorePage(context)
                }
            }
        }
    }
}
