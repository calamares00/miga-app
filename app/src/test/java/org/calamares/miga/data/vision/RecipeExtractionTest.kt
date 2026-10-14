package org.calamares.miga.data.vision

import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeExtractionTest {

    @Test
    fun `several recipes are read with the suggestion`() {
        val result = parseExtractionAnswer(
            """{"together":true,"recipes":[{"name":"Bizcocho","servings":8},{"name":"Cobertura de chocolate"},{"name":""}]}"""
        )
        result as RecipeExtractionResult.Success
        assertEquals(listOf("Bizcocho", "Cobertura de chocolate"), result.recipes.map { it.name })
        assertTrue(result.together)
    }

    @Test
    fun `a single recipe object is accepted and never marked as together`() {
        val result = parseExtractionAnswer("""{"name":"Paella","servings":4}""")
        result as RecipeExtractionResult.Success
        assertEquals("Paella", result.recipes.single().name)
        assertFalse(result.together)
    }

    @Test
    fun `no recipe is an error`() {
        assertTrue(parseExtractionAnswer("""{"together":false,"recipes":[]}""") is RecipeExtractionResult.Error)
    }

    private fun recipe(name: String, prep: Int?, groups: List<String?>, difficulty: String = "EASY", photos: Int = 1) = RecipeVisionResultDto(
        name = name,
        difficulty = difficulty,
        prepTimeMinutes = prep,
        servings = 4,
        notes = "Nota de $name",
        ingredientGroups = groups.map { IngredientGroupDto(it, listOf(IngredientDto("harina", 100.0, "g"))) },
        stepGroups = groups.map { StepGroupDto(it, listOf("Mezclar")) },
        tags = listOf("Postre"),
        utensils = listOf("Horno"),
        dishPhotos = List(photos) { DishPhotoDto(0, listOf(0, 0, 100, 100)) }
    )

    @Test
    fun `joined recipes keep each one as named groups`() {
        val merged = listOf(
            recipe("Bizcocho", 20, listOf(null)),
            recipe("Cobertura", null, listOf(null, "Decoración"), difficulty = "HARD", photos = 3)
        ).mergedIntoOne()
        assertEquals("Bizcocho · Cobertura", merged.name)
        assertEquals(listOf("Bizcocho", "Cobertura", "Cobertura: Decoración"), merged.ingredientGroups.map { it.name })
        assertEquals(listOf("Bizcocho", "Cobertura", "Cobertura: Decoración"), merged.stepGroups.map { it.name })
        assertEquals(20, merged.prepTimeMinutes)
        assertEquals("HARD", merged.difficulty)
        assertEquals(listOf("Postre"), merged.tags)
        assertEquals(listOf("Horno"), merged.utensils)
        assertEquals(3, merged.dishPhotos.size)
        assertEquals("Bizcocho: Nota de Bizcocho\n\nCobertura: Nota de Cobertura", merged.notes)
    }
}
