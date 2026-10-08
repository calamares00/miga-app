package org.calamares.miga.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 -> v5: AI health rating columns (see HealthRating). All nullable, so existing rows are
 * untouched.
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
 * v5 -> v6: downloadable recipe pack columns (see RecipeBook.isPack). NULL means a user's own book.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe_books ADD COLUMN packId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE recipe_books ADD COLUMN packVersion INTEGER DEFAULT NULL")
    }
}

/** v6 -> v7: persistent shopping list table (see ShoppingListItemEntity). */
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
 * v7 -> v8: self-hosted sync server. New `sync_connections` (one per configured server and
 * namespace) and `pending_sync_changes` (outbox of changes to upload) tables; `recipe_books` gains
 * `updatedAt` (last write wins per book) and `syncConnectionId` (null for local books);
 * `recipe_photos` gains `uid` (stable identity to sync photos individually).
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
 * v8 -> v9: recipe photo sync. `pending_sync_changes` gains `parentUid`: when a photo is deleted
 * its row is gone by the time the deletion is uploaded, so the owning recipe must be remembered
 * when the change is queued. Null for books, recipes and photo additions.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pending_sync_changes ADD COLUMN parentUid TEXT DEFAULT NULL")
    }
}

/**
 * v9 -> v10: AI nutrition estimate columns (see NutritionInfo), cached with a content fingerprint
 * like the health rating. All nullable.
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

/** v10 -> v11: personal rating (1-5 stars) of a recipe. Nullable; null means not rated. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN rating INTEGER DEFAULT NULL")
    }
}

/** v11 -> v12: history of items added by hand to the shopping list, used for suggestions. */
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
 * v12 -> v13: shopping list shared through the sync server. Items gain a stable `uid` (random for
 * existing rows), `updatedAt`, a `deletedAt` tombstone and a `syncDirty` flag; `sync_connections`
 * gains `syncShopping` (the connection sharing the list, at most one) and `shoppingPulled` (whether
 * the full list was already downloaded).
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
 * v13 -> v14: optional product photo for shopping list items (`imageUrl`, filled by the barcode
 * scanner) and shopping templates (`shopping_templates`), whose content is stored as text, one item
 * per line.
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
 * v14 -> v15: supermarkets (`shopping_stores`) with a name, a colour and the aisle order
 * (categories, one per line) used to sort the list.
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

/**
 * v15 -> v16: several shopping lists and authorship. Items gain `listUid` ("main" is the default
 * list, where existing items go), `addedBy` and `updatedBy`; `shopping_lists` stores the extra
 * lists.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN listUid TEXT NOT NULL DEFAULT 'main'")
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN addedBy TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN updatedBy TEXT DEFAULT NULL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shopping_lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                uid TEXT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER DEFAULT NULL,
                syncDirty INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shopping_lists_uid ON shopping_lists(uid)")
    }
}

/**
 * v16 -> v17: scanned product details (`productInfo`, ProductInfo as JSON) on shopping list items.
 * Nullable.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN productInfo TEXT DEFAULT NULL")
    }
}

/** v18 -> v19: where the recipe comes from (free text and country code for the flag). Nullable. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipes ADD COLUMN origin TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE recipes ADD COLUMN originCountry TEXT DEFAULT NULL")
    }
}

/** v19 -> v20: books can be pinned to the top of the list. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe_books ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}

/** v20 -> v21: dated personal notes on recipes (see RecipeNoteEntity). */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_journal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`recipeId` INTEGER NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_journal_recipeId` ON `recipe_journal` (`recipeId`)")
    }
}

/** v17 -> v18: difficulty values renamed from Spanish (FACIL/MEDIA/DIFICIL) to EASY/MEDIUM/HARD. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE recipes SET difficulty = CASE difficulty " +
                "WHEN 'FACIL' THEN 'EASY' WHEN 'DIFICIL' THEN 'HARD' WHEN 'MEDIA' THEN 'MEDIUM' ELSE difficulty END"
        )
    }
}

/**
 * Every migration, from the oldest database that can still be upgraded (v4) to the current one,
 * in order. MigaApp opens the database with them and MigrationTest checks that they chain up to
 * [AppDatabase.VERSION] without gaps.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21
)
