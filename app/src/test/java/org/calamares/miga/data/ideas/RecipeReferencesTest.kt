package org.calamares.miga.data.ideas

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeReferencesTest {
    private val names = mapOf(12L to "Lentejas estofadas", 7L to "Tortilla de patatas")

    private fun normalize(text: String) = RecipeReferences.normalize(text, names)

    @Test
    fun `a name followed by its id becomes one link`() {
        assertEquals("Prueba las [[12]] y la ensalada.", normalize("Prueba las Lentejas estofadas (id 12) y la ensalada."))
        assertEquals("Te recomiendo **[[12]]**.", normalize("Te recomiendo **Lentejas estofadas** (ID: 12)."))
        assertEquals("Mira “[[7]]”.", normalize("Mira “Tortilla de patatas” (receta 7)."))
        assertEquals("[[7]], y [[12]]", normalize("Tortilla de patatas [id:7], y [12]"))
    }

    @Test
    fun `loose ids become links`() {
        assertEquals("La [[7]] es ideal.", normalize("La receta #7 es ideal."))
        assertEquals("[[7]] o [[12]], también [[7]]", normalize("receta nº 7 o id 12, también recipeId: 7"))
    }

    @Test
    fun `unknown ids are removed`() {
        assertEquals("Usa [[12]] el lunes y el martes.", normalize("Usa [[12]] el lunes y [[99]] el martes."))
        assertEquals("Hay 2 ideas.", normalize("Hay 2 ideas (id 99)."))
    }

    @Test
    fun `numbers that are not references stay`() {
        val text = "Hornea 3 huevos a 180 ºC durante 20 min. Idea 12: la receta no 3 veces."
        assertEquals(text, normalize(text))
    }

    @Test
    fun `plain text shows names`() {
        assertEquals("Prueba las Lentejas estofadas hoy.", RecipeReferences.toPlainText("Prueba las [[12]] hoy.", names))
        assertEquals(listOf(12L, 7L), RecipeReferences.ids("[[12]] y [[7]]"))
    }
}
