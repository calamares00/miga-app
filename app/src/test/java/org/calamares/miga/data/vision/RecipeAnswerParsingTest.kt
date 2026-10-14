package org.calamares.miga.data.vision

import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.parseLooseNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeAnswerParsingTest {

    private fun parse(text: String) = parseRecipeAnswer(AiText.Success(text)) { "empty" }

    @Test
    fun `decimals in whole-number fields are rounded`() {
        val result = parse("""{"name":"Vinagreta","difficulty":null,"prepTimeMinutes":1.25,"cookTimeMinutes":"10","servings":null}""")
        val recipe = (result as RecipeVisionResult.Success).recipe
        assertEquals(1, recipe.prepTimeMinutes)
        assertEquals(10, recipe.cookTimeMinutes)
        assertEquals(4, recipe.servings)
        assertEquals("MEDIUM", recipe.difficulty)
    }

    @Test
    fun `quantities sent as text, fractions or words are tolerated`() {
        val result = parse(
            """```json
            {"name":"Tortilla","ingredientGroups":[{"name":null,"ingredients":[
              {"name":"huevos","quantity":"4","unit":null},
              {"name":"leche","quantity":"1/2","unit":"vaso"},
              {"name":"harina","quantity":"1 1/2","unit":"tazas"},
              {"name":"sal","quantity":"al gusto"},
              {"name":"aceite","quantity":2.5,"unit":3}
            ]}]}
            ```"""
        )
        val ingredients = (result as RecipeVisionResult.Success).recipe.ingredientGroups.single().ingredients
        assertEquals(listOf(4.0, 0.5, 1.5, null, 2.5), ingredients.map { it.quantity })
        assertNull(ingredients[3].unit)
        assertEquals("3", ingredients[4].unit)
    }

    @Test
    fun `a cut-off answer is a bad response`() {
        val result = parse("""{"name":"Pâtés","ingredientGroups":[{"name":"Pâté","ingredients":[{"name":"hígado","quantity":300,"unit":"g"},{"name":"z""")
        assertTrue(result is RecipeVisionResult.Error)
    }

    @Test
    fun `loose numbers`() {
        assertEquals(1.5, parseLooseNumber("1,5")!!, 0.0001)
        assertEquals(0.75, parseLooseNumber("3/4")!!, 0.0001)
        assertEquals(2.5, parseLooseNumber(" 2 1/2 ")!!, 0.0001)
        assertNull(parseLooseNumber("1/0"))
        assertNull(parseLooseNumber("pizca"))
    }
}
