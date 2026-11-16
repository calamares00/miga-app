package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryMatchTest {

    private fun recipe(id: Long, name: String, vararg ingredients: String, optional: String? = null) = Recipe(
        uid = "u$id", recipeBookId = 1L, recipeBookName = "Libro", name = name, categoryId = null, categoryName = null,
        difficulty = Difficulty.EASY, prepTimeMinutes = null, cookTimeMinutes = null, servings = 2, notes = "", source = "",
        isFavorite = false, timesCooked = 0, createdAt = 0L, updatedAt = 0L, photos = emptyList(),
        ingredientGroups = listOf(
            IngredientGroup(null, ingredients.map { Ingredient(it, null, null) } + listOfNotNull(optional?.let { Ingredient(it, null, null, optional = true) }))
        ),
        stepGroups = emptyList(), tags = emptyList(), utensils = emptyList()
    ).copy(id = id)

    private val tortilla = recipe(1, "Tortilla", "Huevos", "Patatas", "Cebolla", "Aceite de oliva", "Sal", optional = "Pimiento")
    private val salad = recipe(2, "Ensalada", "Tomates", "Lechuga", "Aceite")
    private val cake = recipe(3, "Bizcocho", "Huevos", "Harina", "Azúcar", "Leche")

    @Test
    fun `fewest missing first, basics and optional ingredients never missing`() {
        val matches = recipesWithWhatYouHave(listOf(cake, salad, tortilla), listOf("huevo", "patata", "cebolla", "tomate"))
        assertEquals(listOf("Tortilla", "Ensalada", "Bizcocho"), matches.map { it.recipe.name })
        assertTrue(matches.first().missing.isEmpty())
        assertEquals(listOf("Lechuga"), matches[1].missing)
        assertEquals(3, matches.first().used)
    }

    @Test
    fun `accents, plurals and whole words`() {
        assertEquals(listOf("Bizcocho"), recipesWithWhatYouHave(listOf(cake), listOf("AZUCAR")).map { it.recipe.name })
        // "sal" is not "salsa", and "pan" is not "panceta".
        val sauce = recipe(4, "Pasta", "Salsa de tomate", "Panceta")
        assertTrue(recipesWithWhatYouHave(listOf(sauce), listOf("pan")).isEmpty())
        assertEquals(1, recipesWithWhatYouHave(listOf(sauce), listOf("salsa de tomate")).size)
        assertTrue(recipesWithWhatYouHave(listOf(sauce), listOf(" ")).isEmpty())
    }
}
