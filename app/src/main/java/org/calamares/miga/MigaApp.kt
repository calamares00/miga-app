package org.calamares.miga

import android.content.Context
import android.app.Application
import androidx.room.Room
import org.calamares.miga.crash.CrashReporter
import org.calamares.miga.data.ai.AiKeepAlive
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
import org.calamares.miga.data.local.MIGRATION_17_18
import org.calamares.miga.data.local.MIGRATION_18_19
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.repository.ShoppingContext
import org.calamares.miga.data.sync.SyncEngine
import org.calamares.miga.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MigaApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
            // Only databases older than the first migration may be wiped; a missing migration for a
            // newer version fails loudly instead of silently deleting the user's recipes.
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            .build()
    }

    /**
     * Every locally queued sync change triggers an immediate background upload, on top of the sync
     * on app start and the periodic SyncWorker, so changes reach other devices quickly.
     */
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
        // Installed first so that a crash during the rest of the startup is also captured.
        CrashReporter.install(this)
        AiKeepAlive.init(this)
        applicationScope.launch {
            repository.ensurePhotoUids()
            val seedLanguage = settingsRepository.seedLanguage(L10n.locale().language)
            // Only once: what the user deletes or renames afterwards stays that way.
            if (!settingsRepository.areDefaultsSetUp()) {
                repository.normalizeUtensils(seedLanguage)
                repository.seedDefaultUtensils(seedLanguage)
                repository.seedDefaultCategories(seedLanguage)
                repository.seedIngredientCatalogDefaults(seedLanguage)
                settingsRepository.setDefaultsSetUp()
            }
        }
        SyncWorker.enqueuePeriodic(this)
    }

    /**
     * Best effort: if it fails (offline, server down) the outbox is retried by the next manual,
     * on-start or periodic sync.
     */
    private fun triggerBackgroundSync(connectionId: Long) {
        applicationScope.launch { syncEngine.syncConnection(this@MigaApp, connectionId) }
    }
}
