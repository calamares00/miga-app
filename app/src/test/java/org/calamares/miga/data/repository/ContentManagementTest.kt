package org.calamares.miga.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.ContentKind
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.emptyRecipeDraft
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Renames, merges and deletions of Settings > Manage content against a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ContentManagementTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecipeRepository
    private var bookId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecipeRepository(db)
        bookId = db.recipeBookDao().insert(RecipeBookEntity(uid = "book", name = "Libro", coverPhotoUri = null, createdAt = 0L))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(name: String, category: String?, tags: List<String> = emptyList(), utensils: List<String> = emptyList(), ingredients: List<String> = emptyList()): Long =
        repository.saveRecipe(
            emptyRecipeDraft(bookId).copy(
                name = name,
                categoryName = category,
                tagNames = tags,
                utensilNames = utensils,
                ingredientGroups = listOf(IngredientGroup(null, ingredients.map { Ingredient(it, 1.0, null) }))
            )
        )

    private suspend fun recipe(id: Long): Recipe = repository.observeRecipe(id).first()!!

    private suspend fun item(kind: ContentKind, name: String) = repository.observeContent(kind).first().single { it.name == name }

    @Test
    fun `renaming a category into an existing one merges them and the recipes follow`() = runBlocking {
        val a = save("Flan", "Postre")
        val b = save("Natillas", "Postres")
        repository.renameContent(ContentKind.CATEGORY, item(ContentKind.CATEGORY, "Postre").id, "postres")

        val categories = repository.observeContent(ContentKind.CATEGORY).first().filter { it.name.equals("postres", ignoreCase = true) }
        assertEquals(1, categories.size)
        assertEquals(2, categories.single().usage)
        assertEquals("Postres", recipe(a).categoryName)
        assertEquals("Postres", recipe(b).categoryName)
    }

    @Test
    fun `renaming marks the recipes as changed for sync`() = runBlocking {
        val id = save("Flan", "Postre")
        val before = db.recipeDao().getRecipeOnce(id)!!.updatedAt
        Thread.sleep(5)
        repository.renameContent(ContentKind.CATEGORY, item(ContentKind.CATEGORY, "Postre").id, "Dulces")
        assertTrue(db.recipeDao().getRecipeOnce(id)!!.updatedAt > before)
        assertEquals("Dulces", recipe(id).categoryName)
    }

    @Test
    fun `merging tags keeps a single link on recipes that had both`() = runBlocking {
        val id = save("Flan", null, tags = listOf("Rápido", "rapido"))
        save("Sopa", null, tags = listOf("rapido"))
        val fast = item(ContentKind.TAG, "Rápido")
        val plain = item(ContentKind.TAG, "rapido")
        repository.mergeContent(ContentKind.TAG, listOf(fast.id, plain.id), fast.id)

        assertEquals(listOf("Rápido"), recipe(id).tags)
        assertEquals(2, item(ContentKind.TAG, "Rápido").usage)
    }

    @Test
    fun `renaming an ingredient changes it in the recipes`() = runBlocking {
        val id = save("Ensalada", null, ingredients = listOf("Tomate", "aceite"))
        repository.renameContent(ContentKind.INGREDIENT, item(ContentKind.INGREDIENT, "Tomate").id, "tomate pera")

        assertEquals(listOf("tomate pera", "aceite"), recipe(id).ingredientGroups.single().ingredients.map { it.name })
        assertEquals(1, item(ContentKind.INGREDIENT, "tomate pera").usage)
    }

    @Test
    fun `deleting equipment leaves the recipes without it`() = runBlocking {
        val id = save("Pan", null, utensils = listOf("Horno"))
        // Stored under its canonical name, which depends on the language.
        val oven = repository.observeContent(ContentKind.EQUIPMENT).first().single()
        repository.deleteContent(ContentKind.EQUIPMENT, listOf(oven.id))
        assertTrue(recipe(id).utensils.isEmpty())
        assertNull(repository.observeContent(ContentKind.EQUIPMENT).first().firstOrNull { it.id == oven.id })
    }

    @Test
    fun `a cleanup is applied and undone`() = runBlocking {
        val flan = save("Flan", "merienda", tags = listOf("dulce"))
        save("Bizcocho", "meriendas")
        repository.addContent(ContentKind.TAG, "sin usar")
        val (content, shopping) = repository.contentForCleanup()
        val proposals = org.calamares.miga.data.content.ContentCleanup.propose(content, "es", shopping)

        val snapshot = repository.applyCleanup(proposals)
        assertEquals("Meriendas", recipe(flan).categoryName)
        assertNull(repository.observeContent(ContentKind.TAG).first().firstOrNull { it.name == "sin usar" })

        repository.undoCleanup(snapshot)
        assertEquals("merienda", recipe(flan).categoryName)
        assertEquals(listOf("dulce"), recipe(flan).tags)
        assertEquals(setOf("merienda", "meriendas"), repository.observeContent(ContentKind.CATEGORY).first().map { it.name }.toSet())
        assertEquals(setOf("dulce", "sin usar"), repository.observeContent(ContentKind.TAG).first().map { it.name }.toSet())
    }
}
