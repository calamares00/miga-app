package org.calamares.miga.ui.packs

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.export.PackImportResult
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.support.AppDispatchers

/**
 * Installs, updates and uninstalls of packs, run outside the screens that start them.
 *
 * They used to run in the screen's ViewModel, so leaving the catalogue (back) cancelled them half
 * way, and leaving the app let Android cut their network. Here they run in a scope of their own,
 * with the foreground service of [AiKeepAlive] and its notification (the pack being installed and
 * "n of m"), and say how it went with a notification if the app is in the background. The
 * catalogue shows [progress] and [message] when it is open, also after coming back to it.
 */
object PackJobs {
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)

    private val _progress = MutableStateFlow<BatchProgress?>(null)
    /** The batch running now; null when none. */
    val progress: StateFlow<BatchProgress?> = _progress

    private val _message = MutableStateFlow<String?>(null)
    /** Result of the last batch (what was done, or which packs failed), until dismissed. */
    val message: StateFlow<String?> = _message

    fun dismissMessage() {
        _message.value = null
    }

    /**
     * Installs [entries] (new ones and updates) one after another; [updating] only changes the
     * wording of the result. Does nothing while another batch runs.
     */
    fun installAll(
        context: Context,
        repository: RecipeRepository,
        settingsRepository: SettingsRepository,
        entries: List<PackEntryDto>,
        updating: Boolean
    ) {
        if (entries.isEmpty() || !start(BatchProgress(0, entries.size, uninstalling = false))) return
        val appContext = context.applicationContext
        scope.launch {
            val failed = mutableListOf<String>()
            try {
                AiKeepAlive.hold(L10n.str(R.string.installing_packs), R.drawable.ic_notification_export) {
                    entries.forEachIndexed { index, entry ->
                        _progress.value = BatchProgress(index, entries.size, uninstalling = false)
                        progress(index, entries.size)
                        status(entry.name)
                        val result = PackInstaller.install(appContext, repository, settingsRepository, entry)
                        if (result is PackImportResult.Error) failed += entry.name
                    }
                }
            } finally {
                _progress.value = null
            }
            finish(
                when {
                    failed.isNotEmpty() -> L10n.str(R.string.couldnt_install_packs_x, failed.joinToString(", "))
                    updating -> L10n.str(R.string.x_packs_updated, entries.size)
                    else -> L10n.str(R.string.x_packs_installed, entries.size)
                }
            )
        }
    }

    /** Uninstalls the packs with [packIds]. Does nothing while another batch runs. */
    fun uninstallAll(repository: RecipeRepository, packIds: List<String>) {
        if (packIds.isEmpty() || !start(BatchProgress(0, packIds.size, uninstalling = true))) return
        scope.launch {
            try {
                AiKeepAlive.hold(L10n.str(R.string.uninstalling_packs), R.drawable.ic_notification_export) {
                    packIds.forEachIndexed { index, packId ->
                        _progress.value = BatchProgress(index, packIds.size, uninstalling = true)
                        progress(index, packIds.size)
                        repository.books.uninstallPackById(packId)
                    }
                }
            } finally {
                _progress.value = null
            }
            finish(L10n.str(R.string.x_packs_uninstalled, packIds.size))
        }
    }

    /**
     * Installs one pack from its page. It goes on if the page is closed; the caller awaits the
     * result only while it is still there.
     */
    fun install(
        context: Context,
        repository: RecipeRepository,
        settingsRepository: SettingsRepository,
        entry: PackEntryDto
    ): Deferred<PackImportResult> {
        val appContext = context.applicationContext
        return scope.async {
            val result = AiKeepAlive.hold(L10n.str(R.string.installing_packs), R.drawable.ic_notification_export) {
                status(entry.name)
                PackInstaller.install(appContext, repository, settingsRepository, entry)
            }
            AiKeepAlive.announceIfInBackground(
                when (result) {
                    is PackImportResult.Success -> L10n.str(R.string.x_packs_installed, 1)
                    is PackImportResult.Error -> L10n.str(R.string.couldnt_install_packs_x, entry.name)
                },
                R.drawable.ic_notification_export
            )
            result
        }
    }

    private fun start(first: BatchProgress): Boolean = synchronized(this) {
        if (_progress.value != null) return false
        _progress.value = first
        true
    }

    private fun finish(message: String) {
        _message.value = message
        AiKeepAlive.announceIfInBackground(message, R.drawable.ic_notification_export)
    }
}
