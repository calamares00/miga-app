package com.bmo00.miga.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bmo00.miga.RecetarioApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "miga_periodic_sync"
private const val INTERVAL_MINUTES = 15L

/**
 * Sincroniza en segundo plano, periódicamente, todas las conexiones configuradas (ver Ajustes →
 * Servidor de sincronización). Complementa la subida inmediata al guardar (best-effort, ver
 * RecipeRepository.onSyncChangeEnqueued) y la sincronización automática al abrir la app
 * (RecipeBooksViewModel): esas dos cubren "yo cambié algo", esta cubre "otra app cambió algo
 * mientras esta app estaba en segundo plano", para que llegue sin que el usuario tenga que volver
 * a abrir la pantalla de libros.
 *
 * Si no hay ninguna conexión configurada simplemente no hace nada (vuelve enseguida sin tráfico de
 * red), así que es seguro encolar este trabajo siempre, sin condicionarlo a que exista alguna.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RecetarioApp
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
