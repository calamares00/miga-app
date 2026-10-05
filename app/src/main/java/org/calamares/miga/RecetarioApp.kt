package org.calamares.miga

import android.content.Context
import android.app.Application
import androidx.room.Room
import org.calamares.miga.crash.CrashReporter
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.MIGRATION_4_5
import org.calamares.miga.data.local.MIGRATION_5_6
import org.calamares.miga.data.local.MIGRATION_6_7
import org.calamares.miga.data.local.MIGRATION_7_8
import org.calamares.miga.data.local.MIGRATION_8_9
import org.calamares.miga.data.local.MIGRATION_9_10
import org.calamares.miga.data.local.MIGRATION_10_11
import org.calamares.miga.data.local.MIGRATION_11_12
import org.calamares.miga.data.local.MIGRATION_12_13
import org.calamares.miga.data.local.MIGRATION_13_14
import org.calamares.miga.data.local.MIGRATION_14_15
import org.calamares.miga.data.local.MIGRATION_15_16
import org.calamares.miga.data.local.MIGRATION_16_17
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.repository.ShoppingContext
import org.calamares.miga.data.sync.SyncEngine
import org.calamares.miga.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecetarioApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
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
        RecipeRepository(
            database,
            ShoppingContext(settingsRepository.observeShoppingListUid(), settingsRepository.observeShoppingAuthor())
        ) { connectionId -> triggerBackgroundSync(connectionId) }
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val syncEngine: SyncEngine by lazy { SyncEngine(repository) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        L10n.init(base)
    }

    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        // Lo antes posible, para que un fallo durante el resto del arranque también quede recogido.
        CrashReporter.install(this)
        applicationScope.launch {
            repository.ensurePhotoUids()
            val seedLanguage = settingsRepository.seedLanguage(L10n.locale().language)
            repository.seedDefaultUtensilsIfEmpty(seedLanguage)
            repository.seedDefaultCategoriesIfEmpty(seedLanguage)
            repository.seedIngredientCatalogDefaults(seedLanguage)
        }
        SyncWorker.enqueuePeriodic(this)
    }

    /** Best-effort: si falla (sin red, servidor caído), el outbox lo recoge en el siguiente sync
     *  manual, automático al abrir la app, o periódico (ver SyncWorker) - no hace falta reintentar aquí. */
    private fun triggerBackgroundSync(connectionId: Long) {
        applicationScope.launch { syncEngine.syncConnection(this@RecetarioApp, connectionId) }
    }
}
