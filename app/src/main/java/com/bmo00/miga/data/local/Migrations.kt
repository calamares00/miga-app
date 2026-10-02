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

/**
 * v9 -> v10: añade las columnas de la estimación nutricional con IA (ver NutritionInfo en
 * data/model), mismo mecanismo de caché con huella que la valoración de salud (MIGRATION_4_5).
 * Todas nullable con NULL por defecto.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionCalories INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionProteinGrams REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionCarbsGrams REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionFatGrams REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionFingerprint TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN nutritionAnalyzedAt INTEGER DEFAULT NULL")
    }
}

/**
 * v10 -> v11: añade la valoración personal (1-5 estrellas) de una receta. Nullable con NULL por
 * defecto (sin valorar), mismo mecanismo sencillo que isFavorite pero sin caché/huella (no
 * depende del contenido de la receta, es una opinión del usuario).
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN rating INTEGER DEFAULT NULL")
    }
}

/**
 * v11 -> v12: historial de artículos añadidos a mano a la lista de la compra (sugerencias y
 * "frecuentes" al añadir). Tabla nueva, sin tocar datos existentes.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_history (
                normalizedName TEXT NOT NULL,
                name TEXT NOT NULL,
                lastQuantity REAL,
                lastUnit TEXT,
                uses INTEGER NOT NULL,
                lastUsedAt INTEGER NOT NULL,
                PRIMARY KEY(normalizedName)
            )
            """.trimIndent()
        )
    }
}

/**
 * v12 -> v13: lista de la compra compartida a través del servidor de sincronización. Las filas de
 * `shopping_list_items` ganan identidad estable (`uid`, aleatoria para las ya existentes),
 * `updatedAt` (= createdAt al principio), tombstone `deletedAt` y la marca `syncDirty`; y
 * `sync_connections` gana `syncShopping` (qué conexión comparte la lista, como mucho una) y
 * `shoppingPulled` (si ya se bajó la lista completa del servidor al activarla).
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE shopping_list_items SET uid = lower(hex(randomblob(16)))")
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE shopping_list_items SET updatedAt = createdAt")
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN deletedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN syncDirty INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shopping_list_items_uid ON shopping_list_items(uid)")
        db.execSQL("ALTER TABLE sync_connections ADD COLUMN syncShopping INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_connections ADD COLUMN shoppingPulled INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v13 -> v14: foto opcional de producto en la lista de la compra (`imageUrl`, la rellena el
 * escáner de código de barras con Open Food Facts) y plantillas de lista (`shopping_templates`:
 * "compra semanal"...), cuyo contenido se guarda como texto, una línea por artículo.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN imageUrl TEXT DEFAULT NULL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                body TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * v14 -> v15: supermercados de la lista de la compra (`shopping_stores`): nombre, color y el orden
 * de pasillos (categorías, una por línea) con el que se ordena la lista al elegir esa tienda.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_stores (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL,
                aisleOrder TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
