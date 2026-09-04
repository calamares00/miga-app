package com.bmo00.miga.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bmo00.miga.data.local.dao.CategoryDao
import com.bmo00.miga.data.local.dao.IngredientCatalogDao
import com.bmo00.miga.data.local.dao.IngredientCategoryDao
import com.bmo00.miga.data.local.dao.RecipeBookDao
import com.bmo00.miga.data.local.dao.RecipeDao
import com.bmo00.miga.data.local.dao.TagDao
import com.bmo00.miga.data.local.dao.UtensilDao
import com.bmo00.miga.data.local.entity.CategoryEntity
import com.bmo00.miga.data.local.entity.IngredientCatalogEntity
import com.bmo00.miga.data.local.entity.IngredientCategoryEntity
import com.bmo00.miga.data.local.entity.IngredientEntity
import com.bmo00.miga.data.local.entity.RecipeBookEntity
import com.bmo00.miga.data.local.entity.RecipeEntity
import com.bmo00.miga.data.local.entity.RecipePhotoEntity
import com.bmo00.miga.data.local.entity.RecipeTagCrossRef
import com.bmo00.miga.data.local.entity.RecipeUtensilCrossRef
import com.bmo00.miga.data.local.entity.StepEntity
import com.bmo00.miga.data.local.entity.TagEntity
import com.bmo00.miga.data.local.entity.UtensilEntity

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        UtensilEntity::class,
        RecipePhotoEntity::class,
        RecipeTagCrossRef::class,
        RecipeUtensilCrossRef::class,
        RecipeBookEntity::class,
        IngredientCatalogEntity::class,
        IngredientCategoryEntity::class
    ],
    // La app no escribe migraciones reales (usa fallbackToDestructiveMigration), así que el
    // JSON de esquema de Room no aporta nada, y compilar debug+release a la vez (como hace CI)
    // provoca que kspDebugKotlin y kspReleaseKotlin escriban al mismo fichero en paralelo,
    // dando el error intermitente "Empty schema file". Se desactiva la exportación.
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun utensilDao(): UtensilDao
    abstract fun recipeBookDao(): RecipeBookDao
    abstract fun ingredientCatalogDao(): IngredientCatalogDao
    abstract fun ingredientCategoryDao(): IngredientCategoryDao

    companion object {
        const val DATABASE_NAME = "recetario.db"
    }
}
