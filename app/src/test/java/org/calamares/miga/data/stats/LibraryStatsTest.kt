package org.calamares.miga.data.stats

import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBook
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.StepGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LibraryStatsTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 7, 12, 0) }.timeInMillis
    private val lastYear = Calendar.getInstance().apply { set(2025, Calendar.OCTOBER, 7, 12, 0) }.timeInMillis

    private fun recipe(
        id: Long,
        name: String,
        book: String = "Mamá",
        category: String? = "Arroces",
        minutes: Int? = 30,
        cooked: Int = 0,
        rating: Int? = null,
        ingredients: List<String> = listOf("Arroz", "Sal"),
        tags: List<String> = emptyList(),
        photo: Boolean = true,
        steps: Boolean = true,
        createdAt: Long = now
    ) = Recipe(
        id = id, uid = "u$id", recipeBookId = 1, recipeBookName = book, name = name, categoryId = null, categoryName = category,
        difficulty = Difficulty.EASY, prepTimeMinutes = minutes, cookTimeMinutes = null, servings = 2, notes = "", source = "",
        isFavorite = id % 2 == 0L, timesCooked = cooked, createdAt = createdAt, updatedAt = createdAt,
        photos = if (photo) listOf(RecipePhoto("file:///p/$id.jpg", true)) else emptyList(),
        ingredientGroups = listOf(IngredientGroup(null, ingredients.map { Ingredient(it, null, null) })),
        stepGroups = listOf(StepGroup(null, if (steps) listOf("Cocinar") else emptyList())),
        tags = tags, utensils = emptyList(), rating = rating
    )

    private fun compute(recipes: List<Recipe>) = LibraryStatsCalculator.compute(
        recipes, listOf(RecipeBook(1, "b", "Mamá", null)), now, uncategorized = "Sin categoría", others = "Otras", difficultyLabel = { it.name }
    )

    @Test
    fun `totals, times and rankings`() {
        val stats = compute(
            listOf(
                recipe(1, "Paella", minutes = 60, cooked = 5, rating = 5, tags = listOf("Domingo")),
                recipe(2, "Tortilla", minutes = 20, cooked = 9, rating = 4, ingredients = listOf("huevos", " Sal "), tags = listOf("domingo")),
                recipe(3, "Gazpacho", minutes = null, createdAt = lastYear, ingredients = listOf("Tomate", "sal"))
            )
        )
        assertEquals(3, stats.totalRecipes)
        assertEquals(1, stats.favorites)
        assertEquals(14, stats.timesCookedTotal)
        assertEquals(2, stats.addedThisMonth)
        assertEquals(40, stats.averageMinutes)
        assertEquals(1, stats.quickRecipes)
        assertEquals("Paella", stats.longestRecipe?.name)
        assertEquals(listOf("Tortilla", "Paella"), stats.mostCooked.map { it.name })
        assertEquals(listOf("Paella", "Tortilla"), stats.topRated.map { it.name })
        assertEquals(4.5, stats.averageRating!!, 0.001)
        assertEquals(CountEntry("Sal", 3), stats.topIngredients.first())
        assertEquals(listOf(CountEntry("Domingo", 2)), stats.topTags)
        assertEquals(1, stats.withoutTime)
    }

    @Test
    fun `missing data and categories beyond the limit`() {
        val recipes = (1L..12L).map { recipe(it, "R$it", category = "C$it", photo = it != 1L, steps = it != 2L) } +
            recipe(13, "Sin cat", category = null)
        val stats = compute(recipes)
        assertEquals(8, stats.byCategory.size)
        assertEquals(CountEntry("Otras", 6), stats.byCategory.last())
        assertEquals(1, stats.withoutPhoto)
        assertEquals(1, stats.withoutSteps)
        assertEquals(1, stats.withoutCategory)
        assertTrue(stats.incomplete)
    }

    @Test
    fun `an empty library has no averages`() {
        val stats = compute(emptyList())
        assertNull(stats.averageMinutes)
        assertNull(stats.averageRating)
        assertEquals(0, stats.totalRecipes)
    }

    @Test
    fun `unused photos are the old ones nothing references`() {
        val day = StorageUsageScanner.UNUSED_PHOTO_MIN_AGE_MILLIS
        val referenced = StorageUsageScanner.referencedNames(listOf("file:///data/user/0/app/files/photos/a.jpg", null, "https://x/b.jpg"))
        assertEquals(setOf("a.jpg"), referenced)
        val photos = listOf(
            PhotoFileInfo("a.jpg", 10, now - 2 * day),
            PhotoFileInfo("b.jpg", 20, now - 2 * day),
            PhotoFileInfo("c.jpg", 30, now - 1000)
        )
        assertEquals(listOf("b.jpg"), StorageUsageScanner.unusedPhotos(photos, referenced, now).map { it.name })
    }
}
