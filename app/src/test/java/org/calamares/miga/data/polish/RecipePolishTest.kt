package org.calamares.miga.data.polish

import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.StepGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipePolishTest {

    private val recipe = Recipe(
        id = 1L,
        uid = "uid-1",
        recipeBookId = 1L,
        recipeBookName = "Libro",
        name = "tortilla de patatas",
        categoryId = null,
        categoryName = null,
        difficulty = Difficulty.EASY,
        prepTimeMinutes = null,
        cookTimeMinutes = null,
        servings = 4,
        notes = "",
        source = "",
        isFavorite = false,
        timesCooked = 0,
        createdAt = 0L,
        updatedAt = 0L,
        photos = emptyList(),
        ingredientGroups = listOf(IngredientGroup(null, listOf(Ingredient("Patatas", 1.0, "kg"), Ingredient("huevos", 6.0, null)))),
        stepGroups = listOf(StepGroup(null, listOf("pelar y freir las patatas 20 min", "batir los huevos y cuajar"))),
        tags = emptyList(),
        utensils = emptyList()
    )

    @Test
    fun `the improved texts are read and step numbers removed`() {
        val answer = """
            {"name": "Tortilla de patatas", "notes": "Sírvela templada.",
             "ingredientGroups": [{"name": null, "ingredients": [{"name": "patata", "quantity": 1, "unit": "kg"}, {"name": "huevo", "quantity": 6, "unit": null}]}],
             "stepGroups": [{"name": null, "instructions": ["1. Pela las *patatas* y fríelas **20 minutos**.", "Bate los *huevos*.", "2) Cuaja la tortilla a **fuego medio**."]}],
             "changes": ["Ortografía corregida", "Pasos más claros"]}
        """.trimIndent()
        val polished = decodeAiJson(PolishedRecipeDto.serializer(), answer).toPolished(recipe)
        assertEquals("Tortilla de patatas", polished.name)
        // The recipe had no notes: the model does not get to add some.
        assertEquals("", polished.notes)
        assertEquals(
            listOf("Pela las *patatas* y fríelas **20 minutos**.", "Bate los *huevos*.", "Cuaja la tortilla a **fuego medio**."),
            polished.stepGroups.single().instructions
        )
        assertNull(polished.ingredientGroups.single().ingredients[1].unit)
        assertEquals(2, polished.changes.size)
    }

    @Test(expected = IllegalStateException::class)
    fun `an answer that loses an ingredient is rejected`() {
        val answer = """
            {"name": "Tortilla", "ingredientGroups": [{"name": null, "ingredients": [{"name": "patata", "quantity": 1, "unit": "kg"}]}],
             "stepGroups": [{"name": null, "instructions": ["Uno.", "Dos."]}]}
        """.trimIndent()
        decodeAiJson(PolishedRecipeDto.serializer(), answer).toPolished(recipe)
    }
}
