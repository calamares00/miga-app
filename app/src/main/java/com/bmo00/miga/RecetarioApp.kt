package com.bmo00.miga

import android.app.Application
import androidx.room.Room
import com.bmo00.miga.crash.CrashReporter
import com.bmo00.miga.data.local.AppDatabase
import com.bmo00.miga.data.local.MIGRATION_4_5
import com.bmo00.miga.data.local.MIGRATION_5_6
import com.bmo00.miga.data.local.MIGRATION_6_7
import com.bmo00.miga.data.local.MIGRATION_7_8
import com.bmo00.miga.data.local.MIGRATION_8_9
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.data.sync.SyncEngine
import com.bmo00.miga.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecetarioApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            // Red de seguridad final: si algún día hay un salto de versión sin migración
            // explícita (o un estado corrupto), no crashea, borra y empieza de cero.
            .fallbackToDestructiveMigration()
            .build()
    }

    // El callback dispara una subida en segundo plano justo al encolar un cambio (ver
    // RecipeRepository/triggerBackgroundSync) - complementa el sync automático al abrir la app y
    // el periódico de SyncWorker, para que los cambios propios lleguen a las demás apps Miga sin
    // esperar a ninguno de los otros dos disparadores.
    val repository: RecipeRepository by lazy {
        RecipeRepository(database) { connectionId -> triggerBackgroundSync(connectionId) }
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val syncEngine: SyncEngine by lazy { SyncEngine(repository) }

    override fun onCreate() {
        super.onCreate()
        // Lo antes posible, para que un fallo durante el resto del arranque también quede recogido.
        CrashReporter.install(this)
        applicationScope.launch {
            repository.seedDefaultUtensilsIfEmpty()
            repository.seedDefaultCategoriesIfEmpty()
            repository.seedIngredientCatalogDefaults()
        }
        SyncWorker.enqueuePeriodic(this)
    }

    /** Best-effort: si falla (sin red, servidor caído), el outbox lo recoge en el siguiente sync
     *  manual, automático al abrir la app, o periódico (ver SyncWorker) - no hace falta reintentar aquí. */
    private fun triggerBackgroundSync(connectionId: Long) {
        applicationScope.launch { syncEngine.syncConnection(this@RecetarioApp, connectionId) }
    }
}
