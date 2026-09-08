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

/**
 * v6 -> v7: añade la tabla de la lista de la compra persistente (ver ShoppingListItemEntity).
 * Tabla nueva, no toca ninguna existente.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_list_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                normalizedName TEXT NOT NULL,
                quantity REAL,
                unit TEXT,
                checked INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_list_items_normalizedName ON shopping_list_items(normalizedName)")
    }
}

/**
 * v7 -> v8: servidor self-hosted de sincronización (namespaces privados de lectura-escritura).
 * Tablas nuevas `sync_connections` (una por servidor+namespace configurado en Ajustes) y
 * `pending_sync_changes` (outbox: qué queda por subir); columnas nuevas en `recipe_books`
 * (`updatedAt`, para "última escritura gana" a nivel de libro; `syncConnectionId`, null = libro
 * local) y en `recipe_photos` (`uid`, identidad estable para sincronizar fotos sueltas).
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe_books ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE recipe_books SET updatedAt = createdAt")
        db.execSQL("ALTER TABLE recipe_books ADD COLUMN syncConnectionId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE recipe_photos ADD COLUMN uid TEXT DEFAULT NULL")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_connections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                label TEXT NOT NULL,
                serverUrl TEXT NOT NULL,
                namespaceId TEXT NOT NULL,
                accessToken TEXT NOT NULL,
                lastSyncedRevision INTEGER NOT NULL DEFAULT 0,
                lastSyncedAt INTEGER,
                lastSyncError TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_sync_changes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                syncConnectionId INTEGER NOT NULL,
                entityType TEXT NOT NULL,
                uid TEXT NOT NULL,
                changeType TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_changes_syncConnectionId ON pending_sync_changes(syncConnectionId)")
    }
}

/**
 * v8 -> v9: sincronización de fotos de receta. `pending_sync_changes` gana `parentUid`: para una
 * foto borrada, su fila local ya no existe en el momento de subir el borrado al servidor (se borró
 * junto con la receta al guardar), así que hace falta recordar de qué receta era desde el
 * momento en que se encola el cambio. Null para libros/recetas y para altas de foto (esas sí
 * pueden volver a consultar la fila, que todavía existe).
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pending_sync_changes ADD COLUMN parentUid TEXT DEFAULT NULL")
    }
}
