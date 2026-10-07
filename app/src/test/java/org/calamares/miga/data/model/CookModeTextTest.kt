package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CookModeTextTest {

    private fun highlighted(text: String) = CookModeText.highlights(text).map { text.substring(it) }

    @Test
    fun `times, temperatures, speeds and heat are highlighted`() {
        assertEquals(listOf("20 minutos", "180 ºC"), highlighted("Hornea 20 minutos a 180 ºC hasta que dore."))
        assertEquals(listOf("8-10 min", "fuego medio"), highlighted("Cuece 8-10 min a fuego medio."))
        assertEquals(listOf("5 min", "100°", "velocidad 1"), highlighted("Programa 5 min/100°/velocidad 1."))
        assertEquals(listOf("25 minutes", "350 °F", "medium heat"), highlighted("Bake for 25 minutes at 350 °F over medium heat."))
        assertEquals(listOf("fuego lento", "10 a 12 minutos"), highlighted("Sofríe a fuego lento 10 a 12 minutos"))
    }

    @Test
    fun `plain quantities are not highlighted`() {
        assertEquals(emptyList<String>(), highlighted("Añade 2 huevos y 3 hojas de laurel."))
    }

    private val ingredients = listOf(
        Ingredient("Aceite de oliva virgen extra", 30.0, "ml"),
        Ingredient("Huevos", 4.0, null),
        Ingredient("Diente de ajo", 2.0, null),
        Ingredient("Sal", null, null),
        Ingredient("Pimiento verde", 1.0, null),
        Ingredient("Nata", 200.0, "ml")
    )

    private fun found(step: String) = CookModeText.ingredientsIn(step, ingredients).map { it.name }

    @Test
    fun `ingredients are found by a meaningful word, singular or plural, without accents`() {
        assertEquals(listOf("Aceite de oliva virgen extra", "Diente de ajo"), found("Calienta el aceite y dora los ajos."))
        assertEquals(listOf("Huevos", "Sal"), found("Bate el huevo con una pizca de sal."))
        assertEquals(listOf("Pimiento verde"), found("Añade los PIMIENTOS troceados."))
    }

    @Test
    fun `words that do not name the ingredient do not match`() {
        assertEquals(emptyList<String>(), found("Sirve con hojas verdes y un yogur natural."))
    }
}
