package org.calamares.miga

import android.content.Context
import android.app.Application
import androidx.room.Room
import org.calamares.miga.crash.CrashReporter
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.local.ALL_MIGRATIONS
import org.calamares.miga.data.local.AppDatabase
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
            .addMigrations(*ALL_MIGRATIONS)
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
            settingsRepository.encryptStoredApiKeys()
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
