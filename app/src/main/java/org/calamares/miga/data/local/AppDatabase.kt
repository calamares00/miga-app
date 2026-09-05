package org.calamares.miga.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.calamares.miga.data.local.dao.CategoryDao
import org.calamares.miga.data.local.dao.IngredientCatalogDao
import org.calamares.miga.data.local.dao.IngredientCategoryDao
import org.calamares.miga.data.local.dao.RecipeBookDao
import org.calamares.miga.data.local.dao.RecipeDao
import org.calamares.miga.data.local.dao.TagDao
import org.calamares.miga.data.local.dao.UtensilDao
import org.calamares.miga.data.local.entity.CategoryEntity
import org.calamares.miga.data.local.entity.IngredientCatalogEntity
import org.calamares.miga.data.local.entity.IngredientCategoryEntity
import org.calamares.miga.data.local.entity.IngredientEntity
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.local.entity.RecipeEntity
import org.calamares.miga.data.local.entity.RecipePhotoEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.StepEntity
import org.calamares.miga.data.local.entity.TagEntity
import org.calamares.miga.data.local.entity.UtensilEntity

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
    // El JSON de esquema de Room solo sirve para MigrationTestHelper (pruebas automáticas de
    // migración); las migraciones manuales de Migrations.kt funcionan igual sin él. Se mantiene
    // desactivado porque compilar debug+release a la vez (como hace CI) provoca que
    // kspDebugKotlin y kspReleaseKotlin escriban al mismo fichero en paralelo, dando el error
    // intermitente "Empty schema file".
    version = 6,
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
