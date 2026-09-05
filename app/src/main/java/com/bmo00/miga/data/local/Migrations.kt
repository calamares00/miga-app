package com.bmo00.miga.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 -> v5: añade las columnas de la valoración de salud con IA (ver HealthRating en
 * data/model). Todas nullable con NULL por defecto, así que el ALTER TABLE no necesita
 * reescribir ninguna fila existente ni perder datos.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN healthColor TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN healthDescription TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN healthFingerprint TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN healthAnalyzedAt INTEGER DEFAULT NULL")
    }
}

/**
 * v5 -> v6: añade las columnas de packs de recetas descargables (ver RecipeBook.isPack). Ambas
 * nullable con NULL por defecto; NULL = libro propio del usuario, no un pack instalado.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe_books ADD COLUMN packId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE recipe_books ADD COLUMN packVersion INTEGER DEFAULT NULL")
    }
}
