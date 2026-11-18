package org.calamares.miga.data.health

import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthPrefsTest {

    private fun recipe(vararg ingredients: String) = Recipe(
        id = 1, uid = "u", recipeBookId = 1, recipeBookName = "Libro", name = "Receta", categoryId = null,
        categoryName = null, difficulty = Difficulty.EASY, prepTimeMinutes = null, cookTimeMinutes = null,
        servings = 4, notes = "", source = "", isFavorite = false, timesCooked = 0, createdAt = 0, updatedAt = 0,
        photos = emptyList(), ingredientGroups = listOf(IngredientGroup(null, ingredients.map { Ingredient(it, null, null) })),
        stepGroups = emptyList(), tags = emptyList(), utensils = emptyList()
    )

    @Test
    fun `allergens show all of them by default and warn about nothing`() {
        val health = HealthPrefs()
        assertEquals(AllergenDisplay.ALL, health.allergenDisplay)
        assertTrue(health.listWarnings.isEmpty())
        assertEquals(RecipeFilter(), RecipeFilter().withHealthDefaults(health))
    }

    @Test
    fun `cards warn only about the user's own allergens`() {
        val tortilla = recipe("huevos", "patatas", "aceite de oliva")
        assertEquals(listOf(Allergen.EGGS), tortilla.allergenWarnings(setOf(Allergen.EGGS, Allergen.MILK)))
        assertTrue(tortilla.allergenWarnings(setOf(Allergen.MILK)).isEmpty())
        assertTrue(tortilla.allergenWarnings(emptySet()).isEmpty())
        // Warnings can be switched off while keeping the allergens.
        assertTrue(HealthPrefs(myAllergens = setOf(Allergen.EGGS), warnInLists = false).listWarnings.isEmpty())
    }

    @Test
    fun `searches start with the conditions the user asked for`() {
        val health = HealthPrefs(myAllergens = setOf(Allergen.EGGS), hideMineInSearch = true, coeliacInSearch = true)
        val filter = RecipeFilter(query = "tarta").withHealthDefaults(health)
        assertEquals(setOf(Allergen.EGGS), filter.withoutAllergens)
        assertTrue(filter.coeliacFriendly)
        assertEquals("tarta", filter.query)
        // Picking allergens alone does not hide anything.
        assertEquals(RecipeFilter(), RecipeFilter().withHealthDefaults(HealthPrefs(myAllergens = setOf(Allergen.EGGS))))
    }
}
