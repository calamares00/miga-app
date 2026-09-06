package com.bmo00.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityFormattingTest {

    @Test
    fun `an integer value renders without a decimal point`() {
        assertEquals("3", formatQuantity(3.0))
    }

    @Test
    fun `a value rounds to two decimals`() {
        assertEquals("2.67", formatQuantity(2.666666))
    }

    @Test
    fun `a value with one decimal is not padded with extra zeros`() {
        assertEquals("2.5", formatQuantity(2.5))
    }

    @Test
    fun `formatIngredientText with no quantity is just the name`() {
        assertEquals("Sal", formatIngredientText("Sal", null, null))
    }

    @Test
    fun `formatIngredientText joins quantity, unit and name with de`() {
        assertEquals("2 tazas de harina", formatIngredientText("harina", 2.0, "tazas"))
    }

    @Test
    fun `formatIngredientText omits the unit when blank but keeps the de separator`() {
        // Comportamiento existente (heredado de ui/detail/IngredientFormatting.kt, sin cambios):
        // el " de " se añade siempre que hay cantidad, tenga o no unidad.
        assertEquals("3 de huevos", formatIngredientText("huevos", 3.0, null))
    }

    @Test
    fun `formatIngredientText applies the scale factor`() {
        assertEquals("4 tazas de harina", formatIngredientText("harina", 2.0, "tazas", scale = 2.0))
    }
}
