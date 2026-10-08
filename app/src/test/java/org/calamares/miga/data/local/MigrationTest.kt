package org.calamares.miga.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Database migrations, run on the JVM with Robolectric against the schemas exported to
 * app/schemas (one JSON per version, committed). Each test creates a database as an older version
 * left it, migrates it and lets Room check that the result is exactly the current schema, so a
 * migration that forgets a column or an index fails here instead of on the user's phone.
 *
 * Schemas are exported from version 20 on; older migrations are covered by the chain test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `migrations chain without gaps up to the current version`() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        steps.zipWithNext().forEach { (previous, next) -> assertEquals(previous.second, next.first) }
        steps.forEach { (from, to) -> assertEquals(from + 1, to) }
        assertEquals(AppDatabase.VERSION, steps.last().second)
    }

    @Test
    fun `20 to 21 keeps the recipes and adds the journal`() {
        helper.createDatabase(TEST_DB, 20).use { db ->
            db.execSQL(
                "INSERT INTO recipe_books (id, uid, name, coverPhotoUri, createdAt, updatedAt, isPinned) " +
                    "VALUES (1, 'book-1', 'Casa', NULL, 1, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO recipes (id, uid, name, categoryId, recipeBookId, difficulty, prepTimeMinutes, cookTimeMinutes, " +
                    "servings, notes, source, isFavorite, timesCooked, createdAt, updatedAt) " +
                    "VALUES (7, 'recipe-7', 'Tortilla', NULL, 1, 'EASY', 10, 20, 4, NULL, NULL, 0, 3, 1, 1)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21)

        db.query("SELECT name, timesCooked FROM recipes WHERE id = 7").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Tortilla", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
        }
        db.query("SELECT isPinned FROM recipe_books WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.execSQL("INSERT INTO recipe_journal (recipeId, text, createdAt) VALUES (7, 'Menos sal', 2)")
        db.query("SELECT COUNT(*) FROM recipe_journal WHERE recipeId = 7").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
