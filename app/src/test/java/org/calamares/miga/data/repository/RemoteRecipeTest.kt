package org.calamares.miga.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.RecipeExportDto
import org.calamares.miga.data.export.toDraft
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.sync.RecipeSyncDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Recipes received from miga-server or from an imported file, saved to a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class RemoteRecipeTest {

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

    @Test
    fun `a synced recipe keeps its optional ingredients`() = runBlocking {
        val id = repository.applyRemoteRecipeUpsert(
            RecipeSyncDto(
                uid = "r1",
                bookUid = "book",
                name = "Gazpacho",
                difficulty = "EASY",
                servings = 4,
                ingredientGroups = listOf(
                    IngredientGroupDto(
                        null,
                        listOf(IngredientDto("Tomate", 1.0, "kg"), IngredientDto("Pepino", 1.0, null, optional = true))
                    )
                ),
                updatedAt = 1L
            )
        )!!
        val ingredients = repository.observeRecipe(id).first()!!.ingredientGroups.single().ingredients
        assertEquals(listOf(false, true), ingredients.map { it.optional })
    }

    @Test
    fun `an imported recipe keeps its optional ingredients`() = runBlocking {
        val dto = RecipeExportDto(
            uid = "r2", name = "Ensalada", recipeBookName = "Libro", categoryName = null, difficulty = "EASY",
            prepTimeMinutes = null, cookTimeMinutes = null, servings = 2, notes = "", source = "", isFavorite = false,
            ingredientGroups = listOf(IngredientGroupDto(null, listOf(IngredientDto("Nueces", 50.0, "g", optional = true)))),
            stepGroups = emptyList(), tags = emptyList(), utensils = emptyList()
        )
        val id = repository.saveRecipe(dto.toDraft(bookId))
        assertEquals(true, repository.observeRecipe(id).first()!!.ingredientGroups.single().ingredients.single().optional)
    }
}
