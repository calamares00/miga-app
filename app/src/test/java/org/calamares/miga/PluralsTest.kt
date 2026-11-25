package org.calamares.miga

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.calamares.miga.ui.list.TestMigaApp
import org.robolectric.annotation.Config
import java.util.Locale

/** Counted texts agree with their number in both languages ("1 receta", "3 recetas"). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class PluralsTest {

    private fun resources(language: String) = ApplicationProvider.getApplicationContext<Context>().let { context ->
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        context.createConfigurationContext(config).resources
    }

    @Test
    fun `one and many in Spanish`() {
        val es = resources("es")
        assertEquals("1 receta", es.getQuantityString(R.plurals.x_recipes_count, 1, 1))
        assertEquals("3 recetas", es.getQuantityString(R.plurals.x_recipes_count, 3, 3))
        assertEquals("1 vez", es.getQuantityString(R.plurals.stats_times_x, 1, 1))
        assertEquals("¿Eliminar 1 libro?", es.getQuantityString(R.plurals.delete_books_title_x, 1, 1))
        assertEquals(
            "«Postres»: 1 receta nueva, 2 actualizadas",
            es.getString(
                R.string.shared_book_imported_x_x_x,
                "Postres",
                es.getQuantityString(R.plurals.new_recipes_n, 1, 1),
                es.getQuantityString(R.plurals.updated_recipes_n, 2, 2)
            )
        )
    }

    @Test
    fun `one and other in English`() {
        val en = resources("en")
        assertEquals("1 recipe", en.getQuantityString(R.plurals.x_recipes_count, 1, 1))
        assertEquals("0 recipes", en.getQuantityString(R.plurals.x_recipes_count, 0, 0))
        assertEquals("Preparing 2 photos…", en.getQuantityString(R.plurals.ai_step_preparing_photos_n, 2, 2))
        assertEquals("Analysis finished for 4 recipes, with 1 error", en.getQuantityString(
            R.plurals.bulk_ai_done_with_errors, 4, 4, en.getQuantityString(R.plurals.errors_n, 1, 1)
        ))
    }
}
