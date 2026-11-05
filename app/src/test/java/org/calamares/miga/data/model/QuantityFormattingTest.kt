package org.calamares.miga.data.model

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
        assertEquals("2 tazas de harina", formatIngredientText("harina", 2.0, "tazas", language = "es"))
    }

    @Test
    fun `formatIngredientText omits the de separator when there is no unit`() {
        assertEquals("3 huevos", formatIngredientText("huevos", 3.0, null, language = "es"))
    }

    @Test
    fun `formatIngredientText omits the de separator when unit is blank`() {
        assertEquals("3 huevos", formatIngredientText("huevos", 3.0, "  ", language = "es"))
    }

    @Test
    fun `formatIngredientText applies the scale factor`() {
        assertEquals("4 tazas de harina", formatIngredientText("harina", 2.0, "tazas", scale = 2.0, language = "es"))
    }

    @Test
    fun `formatIngredientText uses no connector in English`() {
        assertEquals("2 cups flour", formatIngredientText("flour", 2.0, "cups", language = "en"))
    }

    @Test
    fun `under an hour stays in minutes`() {
        assertEquals("0 min", formatDuration(0))
        assertEquals("45 min", formatDuration(45))
        assertEquals("59 min", formatDuration(59))
    }

    @Test
    fun `from 60 minutes on it goes to hours`() {
        assertEquals("1 h", formatDuration(60))
        assertEquals("1 h 1 min", formatDuration(61))
        assertEquals("2 h", formatDuration(120))
        assertEquals("3 h 50 min", formatDuration(230))
    }

    @Test
    fun `a negative time is shown as zero`() {
        assertEquals("0 min", formatDuration(-5))
    }

    @Test
    fun `a small positive amount is not shown as zero`() {
        assertEquals("0.004", formatQuantity(0.004))
        assertEquals("0.0012", formatQuantity(0.0012345))
        assertEquals("0.01", formatQuantity(0.006))
        assertEquals("0", formatQuantity(0.0))
    }
}
