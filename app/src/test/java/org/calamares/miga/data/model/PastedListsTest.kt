package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastedListsTest {

    @Test
    fun `markers are removed but quantities kept`() {
        val text = "- 200 g harina\n• 3 huevos\n\n1. 1.5 kg patatas\n2) Sal\nPaso 3: Hornear"
        assertEquals(listOf("200 g harina", "3 huevos", "1.5 kg patatas", "Sal", "Hornear"), PastedLists.lines(text))
    }

    @Test
    fun `typing Enter is not a paste`() {
        assertNull(PastedLists.pastedLines("Bate los huevos", "Bate los huevos\n"))
        assertNull(PastedLists.pastedLines("", "Una sola línea pegada"))
        assertEquals(listOf("Bate los huevos", "Añade el azúcar"), PastedLists.pastedLines("", "1. Bate los huevos\n2. Añade el azúcar"))
    }

    @Test
    fun `ingredient lines are split into quantity, unit and name`() {
        assertEquals(ParsedShoppingEntry("harina", 200.0, "g"), PastedLists.ingredient("200 g de harina"))
        assertEquals(ParsedShoppingEntry("huevos", 3.0, null), PastedLists.ingredient("3 huevos"))
        assertEquals(ParsedShoppingEntry("Sal al gusto", null, null), PastedLists.ingredient("Sal al gusto"))
    }
}
