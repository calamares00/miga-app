package com.bmo00.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeFilteringTest {

    private fun recipe(
        id: Long = 1L,
        name: String = "Receta",
        categoryName: String? = null,
        difficulty: Difficulty = Difficulty.MEDIA,
        tags: List<String> = emptyList(),
        utensils: List<String> = emptyList(),
        ingredientNames: List<String> = emptyList(),
        isFavorite: Boolean = false,
        timesCooked: Int = 0,
        createdAt: Long = 0L,
        prepTimeMinutes: Int? = null
    ) = Recipe(
        id = id,
        uid = "uid-$id",
        recipeBookId = 1L,
        recipeBookName = "Libro",
        name = name,
        categoryId = null,
        categoryName = categoryName,
        difficulty = difficulty,
        prepTimeMinutes = prepTimeMinutes,
        cookTimeMinutes = null,
        servings = 4,
        notes = "",
        source = "",
        isFavorite = isFavorite,
        timesCooked = timesCooked,
        createdAt = createdAt,
        updatedAt = createdAt,
        photos = emptyList(),
        ingredientGroups = listOf(IngredientGroup(null, ingredientNames.map { Ingredient(it, null, null) })),
        stepGroups = emptyList(),
        tags = tags,
        utensils = utensils
    )

    @Test
    fun `query matches name case-insensitively`() {
        val recipes = listOf(recipe(id = 1, name = "Tarta de manzana"), recipe(id = 2, name = "Sopa"))
        val result = recipes.applyFilter(RecipeFilter(query = "TARTA"))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `query matches an ingredient name`() {
        val recipes = listOf(
            recipe(id = 1, name = "A", ingredientNames = listOf("Tomate")),
            recipe(id = 2, name = "B", ingredientNames = listOf("Pollo"))
        )
        val result = recipes.applyFilter(RecipeFilter(query = "tomate"))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `category filter falls back to the uncategorized label`() {
        val recipes = listOf(recipe(id = 1, categoryName = null), recipe(id = 2, categoryName = "Postres"))
        val result = recipes.applyFilter(RecipeFilter(categoryNames = setOf(UNCATEGORIZED_CATEGORY_LABEL)))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `difficulty filter is an OR across the selected values`() {
        val recipes = listOf(
            recipe(id = 1, difficulty = Difficulty.FACIL),
            recipe(id = 2, difficulty = Difficulty.DIFICIL),
            recipe(id = 3, difficulty = Difficulty.MEDIA)
        )
        val result = recipes.applyFilter(RecipeFilter(difficulties = setOf(Difficulty.FACIL, Difficulty.DIFICIL)))
        assertEquals(setOf(1L, 2L), result.map { it.id }.toSet())
    }

    @Test
    fun `utensils filter requires ALL selected utensils`() {
        val recipes = listOf(
            recipe(id = 1, utensils = listOf("Horno", "Batidora")),
            recipe(id = 2, utensils = listOf("Horno"))
        )
        val result = recipes.applyFilter(RecipeFilter(utensils = setOf("Horno", "Batidora")))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `ingredients filter matches ignoring case and requires ALL`() {
        val recipes = listOf(
            recipe(id = 1, ingredientNames = listOf("Tomate", "Cebolla")),
            recipe(id = 2, ingredientNames = listOf("Tomate"))
        )
        val result = recipes.applyFilter(RecipeFilter(ingredients = setOf("tomate", "CEBOLLA")))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `only favorites filter excludes non-favorites`() {
        val recipes = listOf(recipe(id = 1, isFavorite = true), recipe(id = 2, isFavorite = false))
        val result = recipes.applyFilter(RecipeFilter(onlyFavorites = true))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `sort by name is case-insensitive ascending`() {
        val recipes = listOf(recipe(id = 1, name = "zeta"), recipe(id = 2, name = "Alfa"))
        val result = recipes.applyFilter(RecipeFilter(sortOption = SortOption.NAME_ASC))
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun `sort by most cooked is descending`() {
        val recipes = listOf(recipe(id = 1, timesCooked = 2), recipe(id = 2, timesCooked = 5))
        val result = recipes.applyFilter(RecipeFilter(sortOption = SortOption.MOST_COOKED))
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun `sort by prep time treats a missing time as the slowest`() {
        val recipes = listOf(recipe(id = 1, prepTimeMinutes = null), recipe(id = 2, prepTimeMinutes = 10))
        val result = recipes.applyFilter(RecipeFilter(sortOption = SortOption.PREP_TIME))
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }
}
