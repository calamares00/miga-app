package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingSuggestionsTest {

    private val history = listOf(
        ShoppingSuggestion("Tomate", 2.0, "kg", 5),
        ShoppingSuggestion("Salsa de tomate", 1.0, "bote", 2)
    )
    private val catalog = listOf("Tomate", "Tomillo", "Cebolla", "Tomate frito")

    @Test
    fun `prefix matches come before contains matches and history before catalog`() {
        val names = ShoppingSuggestions.rank("tom", history, catalog).map { it.name }
        assertEquals(listOf("Tomate", "Tomate frito", "Tomillo", "Salsa de tomate"), names)
    }

    @Test
    fun `history entry wins over the same catalog name and keeps its last quantity`() {
        val tomate = ShoppingSuggestions.rank("tomate", history, catalog).first()
        assertEquals("Tomate", tomate.name)
        assertEquals("kg", tomate.unit)
    }

    @Test
    fun `blank query gives nothing`() {
        assertEquals(0, ShoppingSuggestions.rank("  ", history, catalog).size)
    }

    @Test
    fun `limit is respected`() {
        assertEquals(2, ShoppingSuggestions.rank("tom", history, catalog, limit = 2).size)
    }
}
