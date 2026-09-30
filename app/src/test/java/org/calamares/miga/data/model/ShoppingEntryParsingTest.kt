package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingEntryParsingTest {

    private fun single(text: String) = ShoppingEntryParser.parse(text).single()

    @Test
    fun `plain name has no quantity or unit`() {
        assertEquals(ParsedShoppingEntry("leche", null, null), single("leche"))
    }

    @Test
    fun `quantity and unit are detected and de is dropped`() {
        assertEquals(ParsedShoppingEntry("tomates", 2.0, "kg"), single("2 kg de tomates"))
    }

    @Test
    fun `unit attached to the number and aliases are normalized`() {
        assertEquals(ParsedShoppingEntry("harina", 500.0, "g"), single("500gr harina"))
        assertEquals(ParsedShoppingEntry("aceite", 2.0, "l"), single("2 litros aceite"))
    }

    @Test
    fun `quantity without unit keeps the rest as name`() {
        assertEquals(ParsedShoppingEntry("huevos", 3.0, null), single("3 huevos"))
    }

    @Test
    fun `decimal comma is a number and not a separator`() {
        assertEquals(ParsedShoppingEntry("queso", 1.5, "kg"), single("1,5 kg queso"))
    }

    @Test
    fun `fractions are supported`() {
        assertEquals(ParsedShoppingEntry("cebolla", 0.5, null), single("1/2 cebolla"))
        assertEquals(ParsedShoppingEntry("sandía", 0.5, null), single("½ sandía"))
    }

    @Test
    fun `number glued to a non-unit word is part of the name`() {
        assertEquals(ParsedShoppingEntry("7up", null, null), single("7up"))
    }

    @Test
    fun `commas semicolons and newlines separate entries`() {
        val result = ShoppingEntryParser.parse("leche, 2 kg tomates;pan\n3 huevos")
        assertEquals(listOf("leche", "tomates", "pan", "huevos"), result.map { it.name })
        assertEquals(2.0, result[1].quantity)
    }

    @Test
    fun `and only separates when requested`() {
        assertEquals(1, ShoppingEntryParser.parse("sal y pimienta").size)
        assertEquals(listOf("sal", "pimienta"), ShoppingEntryParser.parse("sal y pimienta", splitOnY = true).map { it.name })
    }

    @Test
    fun `blank input gives no entries`() {
        assertEquals(0, ShoppingEntryParser.parse("  , ;\n ").size)
    }

    @Test
    fun `lone number stays as name`() {
        assertNull(single("3").quantity)
    }
}
