package org.calamares.miga.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.calamares.miga.MigaApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "miga_periodic_sync"
private const val INTERVAL_MINUTES = 15L

/**
 * Periodically syncs every configured connection in the background (Settings > Sync server). It
 * complements the immediate upload on save (see RecipeRepository.onSyncChangeEnqueued) and the
 * automatic sync when the app opens (RecipeBooksViewModel): those cover local changes, this one
 * brings in changes made on other devices while the app is in the background. With no connection
 * configured it returns at once without network traffic, so it is always safe to enqueue.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MigaApp
        val repository = app.repository
        val syncEngine = SyncEngine(repository)
        repository.observeSyncConnections().first().forEach { connection ->
            syncEngine.syncConnection(applicationContext, connection.id)
        }
        return Result.success()
    }

    companion object {
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
