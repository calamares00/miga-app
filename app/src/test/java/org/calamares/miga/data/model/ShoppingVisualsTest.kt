package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShoppingVisualsTest {

    @Test
    fun `known categories ignore case and accents`() {
        assertEquals("🥛", ShoppingVisuals.categoryStyle("Lácteos").emoji)
        assertEquals("🥛", ShoppingVisuals.categoryStyle("lacteos").emoji)
        assertEquals("🍯", ShoppingVisuals.categoryStyle("Azúcares y edulcorantes").emoji)
    }

    @Test
    fun `uncategorized label maps to the generic style`() {
        assertEquals("🛒", ShoppingVisuals.categoryStyle(UNCATEGORIZED_INGREDIENT_LABEL).emoji)
    }

    @Test
    fun `unknown category gets a stable style`() {
        assertEquals(ShoppingVisuals.categoryStyle("Mascotas"), ShoppingVisuals.categoryStyle("Mascotas"))
        assertEquals("🛍️", ShoppingVisuals.categoryStyle("Mascotas").emoji)
    }

    @Test
    fun `item emoji matches whole words, with or without accents`() {
        assertEquals("🍅", ShoppingVisuals.itemEmoji("Tomates cherry", "Verduras"))
        assertEquals("🍌", ShoppingVisuals.itemEmoji("Plátano de Canarias", "Frutas"))
        assertEquals("🍍", ShoppingVisuals.itemEmoji("Piña", "Frutas"))
    }

    @Test
    fun `short keywords do not match inside other words`() {
        // "sal" must not match "salmón", nor "te" match "tomate"
        assertEquals("🐟", ShoppingVisuals.itemEmoji("Salmón", "Pescados"))
        assertNotEquals("🍵", ShoppingVisuals.itemEmoji("Tomate triturado", "Conservas"))
    }

    @Test
    fun `item without a known word falls back to its category emoji`() {
        assertEquals("🥫", ShoppingVisuals.itemEmoji("Fabada asturiana", "Conservas"))
    }
}
