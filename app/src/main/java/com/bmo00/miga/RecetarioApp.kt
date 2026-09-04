package com.bmo00.miga

import android.app.Application
import androidx.room.Room
import com.bmo00.miga.data.local.AppDatabase
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
