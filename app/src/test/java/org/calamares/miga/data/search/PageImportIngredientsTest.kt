package org.calamares.miga.data.search

import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.vision.RecipeVisionResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** annarecetasfaciles.com's prawn cocktail: the page's data has the 7 ingredients but no steps. */
class PageImportIngredientsTest {

    private fun ingredients(vararg items: Triple<String, Double?, String?>) =
        listOf(IngredientGroupDto(null, items.map { (name, quantity, unit) -> IngredientDto(name, quantity, unit) }))

    private val standard = RecipeVisionResultDto(
        name = "Cóctel de langostinos",
        ingredientGroups = ingredients(
            Triple("lechuga", 5.0, "hojas"),
            Triple("langostinos cocidos pelados", 200.0, "g"),
            Triple("piña", 2.0, "rodajas"),
            Triple("aguacate", 0.5, null),
            Triple("salsa cocktail", null, null),
            Triple("huevas negras", null, null),
            Triple("cebollino fresco", null, null)
        )
    )

    @Test
    fun `the page's ingredients are kept when the model leaves some out`() {
        val fromModel = RecipeVisionResultDto(
            name = "Cóctel de langostinos",
            ingredientGroups = ingredients(
                Triple("huevas negras", null, null),
                Triple("cebollino fresco", null, null),
                Triple("salsa cocktail", null, null)
            )
        )
        val kept = fromModel.keepingIngredientsOf(standard)
        assertEquals(standard.ingredientGroups, kept.ingredientGroups)
        assertEquals("Cóctel de langostinos", kept.name)
    }

    @Test
    fun `a complete answer from the model stays as it is`() {
        val grouped = RecipeVisionResultDto(
            name = "Cóctel de langostinos",
            ingredientGroups = standard.ingredientGroups.map { it.copy(name = "Para el cóctel") }
        )
        assertEquals(grouped, grouped.keepingIngredientsOf(standard))
    }

    @Test
    fun `the model is told which ingredients the page lists`() {
        val prompt = buildUrlImportPrompt("https://example.com", "texto", standard = standard)
        assertTrue(prompt.contains("keep every one of them"))
        assertTrue(prompt.contains("5 hojas lechuga"))
        assertTrue(prompt.contains("0.5 aguacate"))
        assertTrue(prompt.contains("cebollino fresco"))
        assertTrue(!buildUrlImportPrompt("https://example.com", "texto").contains("keep every one of them"))
    }
}
