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

    @Test
    fun `speeds with fractions and ranges are highlighted whole`() {
        val text = "Trocee 6 seg, velocidad 3½. Luego velocidad 5-10."
        val parts = CookModeText.highlights(text).map { text.substring(it) }
        org.junit.Assert.assertTrue(parts.toString(), "velocidad 3½" in parts)
        org.junit.Assert.assertTrue(parts.toString(), "velocidad 5-10" in parts)
    }

    @Test
    fun `every way of saying the heat or the hob's level is highlighted whole`() {
        assertEquals(listOf("fuego medio-fuerte", "8 a 10 minutos"), highlighted("Tapar y cocinar a fuego medio-fuerte durante unos 8 a 10 minutos para que reduzca."))
        assertEquals(listOf("fuego al máximo (nivel 9 de 9)"), highlighted("Subir el fuego al máximo (nivel 9 de 9) y añadir el pollo."))
        assertEquals(listOf("fuego medio (nivel 6 de 9)"), highlighted("Calentar a fuego medio (nivel 6 de 9)."))
        assertEquals(listOf("fuego mínimo"), highlighted("Dejar a fuego mínimo."))
        assertEquals(listOf("potencia 7", "2 minutos"), highlighted("Calentar en el microondas a potencia 7 durante 2 minutos"))
        assertEquals(listOf("toda la noche"), highlighted("Dejar en remojo toda la noche."))
        assertEquals(listOf("medium-high heat", "level 5 of 9"), highlighted("Cook over medium-high heat, level 5 of 9."))
    }

    @Test
    fun `the full stop after a time is not part of it, the dot of an abbreviation in a sentence is`() {
        assertEquals(listOf("1 o 2 minutos"), highlighted("Saltear todo junto durante 1 o 2 minutos."))
        assertEquals(listOf("5 min"), highlighted("Hornear 5 min."))
        assertEquals(listOf("5 min."), highlighted("Hornear 5 min. y dar la vuelta."))
    }

    private val ajillo = listOf(
        Ingredient("Contramuslos de pollo troceados", 1.2, "kg"),
        Ingredient("Cabezas de ajo", 2.0, null),
        Ingredient("Vino blanco", 150.0, "ml"),
        Ingredient("Perejil seco", null, null),
        Ingredient("Aceite de oliva virgen extra", null, null),
        Ingredient("Sal", null, null)
    )

    private fun mentioned(step: String) = CookModeText.ingredientMentions(step, ajillo).map { step.substring(it) }

    @Test
    fun `ingredients named in a step are found with the words that join them`() {
        assertEquals(listOf("contramuslos de pollo"), mentioned("Añadir los contramuslos de pollo troceados y dorarlos."))
        assertEquals(listOf("ajos", "pollo"), mentioned("Reincorporar los ajos y saltear con el pollo."))
        assertEquals(listOf("vino", "pollo"), mentioned("Verter el vino blanco hasta cubrir la mitad del pollo."))
        assertEquals(listOf("perejil", "aceite", "sal"), mentioned("Añadir el perejil seco, el aceite y la sal."))
    }

    @Test
    fun `words that only look like an ingredient are not taken`() {
        assertEquals(emptyList<String>(), mentioned("Salir de la cocina y secar la sartén en un lugar seco."))
    }
}
