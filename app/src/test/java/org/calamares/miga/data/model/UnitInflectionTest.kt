package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitInflectionTest {

    private fun es(unit: String, quantity: Double) = UnitInflection.inflect(unit, quantity, "es")
    private fun en(unit: String, quantity: Double) = UnitInflection.inflect(unit, quantity, "en")

    @Test
    fun `more than one takes the plural`() {
        assertEquals("tazas", es("taza", 3.0))
        assertEquals("tazas", es("taza", 1.5))
        assertEquals("cups", en("cup", 2.0))
        assertEquals("pinches", en("pinch", 2.0))
    }

    @Test
    fun `one or less keeps the singular`() {
        assertEquals("taza", es("taza", 1.0))
        assertEquals("taza", es("taza", 0.5))
        assertEquals("cup", en("cup", 0.25))
    }

    @Test
    fun `a plural unit goes back to singular for one`() {
        assertEquals("taza", es("tazas", 1.0))
        assertEquals("cucharadita", es("cucharaditas", 0.5))
        assertEquals("cup", en("cups", 1.0))
    }

    @Test
    fun `symbols and abbreviations never change`() {
        listOf("g", "kg", "ml", "l", "cda", "cdas", "tbsp", "tsp", "oz", "lb", "c/s").forEach {
            assertEquals(it, es(it, 3.0))
            assertEquals(it, en(it, 3.0))
        }
    }

    @Test
    fun `adjectives agree and complements after de do not`() {
        assertEquals("cucharadas soperas", es("cucharada sopera", 2.0))
        assertEquals("dientes de ajo", es("diente de ajo", 3.0))
        assertEquals("cucharada rasa", es("cucharadas rasas", 1.0))
    }

    @Test
    fun `irregular plurals from the table`() {
        assertEquals("unidades", es("unidad", 2.0))
        assertEquals("nueces", es("nuez", 2.0))
        assertEquals("loaves", en("loaf", 2.0))
        assertEquals("leaves", en("leaf", 3.0))
    }

    @Test
    fun `unknown lower-case words get the regular plural`() {
        assertEquals("puñaditos", es("puñadito", 2.0))
        assertEquals("racimos", es("racimo", 2.0))
        assertEquals("tazones", es("tazón", 2.0))
        assertEquals("berries", en("berry", 2.0))
    }

    @Test
    fun `unknown words are never forced to singular`() {
        assertEquals("racimos", es("racimos", 1.0))
    }

    @Test
    fun `a leading capital is kept`() {
        assertEquals("Tazas", es("Taza", 2.0))
    }

    @Test
    fun `the ingredient text uses the agreeing unit`() {
        assertEquals("3 tazas de agua", formatIngredientText("agua", 3.0, "taza", language = "es"))
        assertEquals("1 taza de agua", formatIngredientText("agua", 1.0, "taza", language = "es"))
        assertEquals("0.5 taza de agua", formatIngredientText("agua", 0.5, "taza", language = "es"))
        assertEquals("200 g de harina", formatIngredientText("harina", 200.0, "g", language = "es"))
        assertEquals("2 cups flour", formatIngredientText("flour", 2.0, "cup", language = "en"))
    }

    @Test
    fun `the scaled quantity decides`() {
        assertEquals("1 taza de agua", formatIngredientText("agua", 0.5, "taza", scale = 2.0, language = "es"))
        assertEquals("2 tazas de agua", formatIngredientText("agua", 1.0, "taza", scale = 2.0, language = "es"))
        assertEquals("1 taza de agua", formatIngredientText("agua", 2.0, "tazas", scale = 0.5, language = "es"))
    }
}
