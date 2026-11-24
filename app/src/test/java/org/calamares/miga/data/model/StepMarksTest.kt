package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Bold and italics that only repeat what the app styles by itself go; warnings stay. */
class StepMarksTest {

    private val ingredients = listOf(Ingredient("Contramuslos de pollo", 1.2, "kg"), Ingredient("Cebolla", 1.0, null), Ingredient("Ajo", 2.0, null))

    private fun clean(text: String) = StepMarks.withoutRedundantMarks(text, ingredients)

    @Test
    fun `marks over times, heat and settings go`() {
        assertEquals("Cocinar 8 a 10 minutos a fuego medio.", clean("Cocinar **8 a 10 minutos** a **fuego medio**."))
        assertEquals("Subir el fuego al máximo (nivel 9 de 9).", clean("Subir el fuego al **máximo** (**nivel 9 de 9**)."))
        assertEquals("Tapar a fuego medio-fuerte.", clean("Tapar a **fuego medio**-**fuerte**."))
    }

    @Test
    fun `italics over ingredients go`() {
        assertEquals("Dorar los contramuslos de pollo con la cebolla y el ajo.", clean("Dorar los *contramuslos de pollo* con la *cebolla* y el *ajo*."))
    }

    @Test
    fun `warnings and other marks stay`() {
        assertEquals("Dorar **sin moverlos** 5 minutos.", clean("Dorar **sin moverlos** **5 minutos**."))
        assertEquals("Servir *bien caliente*.", clean("Servir *bien caliente*."))
        assertEquals("Un \\*asterisco\\* escrito.", clean("Un \\*asterisco\\* escrito."))
    }
}
