package org.calamares.miga.data.ideas

import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class RecipeIdeasTest {

    private fun recipe(id: Long, name: String, favorite: Boolean = false, timesCooked: Int = 0) = Recipe(
        id = id, uid = "u$id", recipeBookId = 1, recipeBookName = "Libro", name = name,
        categoryId = null, categoryName = "Arroces", difficulty = Difficulty.EASY,
        prepTimeMinutes = 10, cookTimeMinutes = 20, servings = 4, notes = "", source = "",
        isFavorite = favorite, timesCooked = timesCooked, createdAt = 0, updatedAt = id, photos = emptyList(),
        ingredientGroups = listOf(IngredientGroup(null, listOf(Ingredient("arroz", 300.0, "g"), Ingredient("pollo", null, null)))),
        stepGroups = emptyList(), tags = listOf("valenciana"), utensils = emptyList()
    )

    @Test
    fun `invented recipe ids are dropped and empty sections removed`() {
        val dto = IdeasAnswerDto(
            title = " Menú ",
            sections = listOf(
                IdeaSectionDto("Lunes", listOf(IdeaRecipeDto(1, "Comida", " "), IdeaRecipeDto(99, "Cena"))),
                IdeaSectionDto("Martes", listOf(IdeaRecipeDto(42)))
            ),
            tips = listOf(" ", "Remoja las legumbres"),
            newDishes = listOf(NewDishIdeaDto("", "x"), NewDishIdeaDto("Gazpacho", "Frío"))
        )
        val answer = dto.toAnswer(setOf(1L, 2L))
        assertEquals("Menú", answer.title)
        assertEquals(1, answer.sections.size)
        assertEquals(listOf(IdeaRecipe(1, "Comida", null)), answer.sections.single().recipes)
        assertEquals(listOf("Remoja las legumbres"), answer.tips)
        assertEquals(listOf(NewDishIdea("Gazpacho", "Frío")), answer.newDishes)
        assertEquals(listOf(1L), answer.recipeIds)
    }

    @Test
    fun `recipes are described in one compact line`() {
        assertEquals(
            "7 | Paella | category: Arroces | tags: valenciana | 30 min | favourite | ingredients: arroz, pollo",
            describeRecipe(recipe(7, "Paella", favorite = true))
        )
    }

    @Test
    fun `large libraries keep favourites and most cooked recipes`() {
        val recipes = (1L..400L).map { recipe(it, "R$it", favorite = it == 5L, timesCooked = if (it == 6L) 9 else 0) }
        val catalog = selectCatalog(recipes)
        assertEquals(300, catalog.size)
        assertEquals(listOf(5L, 6L), catalog.take(2).map { it.id })
    }

    @Test
    fun `prompt includes the library, the request and the season hint`() {
        val prompt = buildIdeasPrompt(listOf(recipe(3, "Arroz al horno")), "", IdeasPreset.SEASONAL, emptyList(), LocalDate.of(2026, 10, 7), Locale("es", "ES"))
        assertTrue(prompt.contains("3 | Arroz al horno"))
        assertTrue(prompt.contains("October"))
        assertTrue(prompt.contains("\"ES\""))
    }
}
