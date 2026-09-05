package com.bmo00.miga

import android.app.Application
import androidx.room.Room
import com.bmo00.miga.data.local.AppDatabase
import com.bmo00.miga.data.local.MIGRATION_4_5
import com.bmo00.miga.data.local.MIGRATION_5_6
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RecetarioApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            // Red de seguridad final: si algún día hay un salto de versión sin migración
            // explícita (o un estado corrupto), no crashea, borra y empieza de cero.
            .fallbackToDestructiveMigration()
            .build()
    }

    val repository: RecipeRepository by lazy { RecipeRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.seedDefaultUtensilsIfEmpty()
            repository.seedDefaultCategoriesIfEmpty()
            repository.seedIngredientCatalogDefaults()
        }
    }
}
