package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingAisleOrderTest {

    private fun group(name: String) = ShoppingListGroup(name, emptyList())

    @Test
    fun `groups follow the store order and the rest go after alphabetically`() {
        val groups = listOf(group("Bebidas"), group("Mascotas"), group("Frutas"), group("Lácteos"), group(UNCATEGORIZED_INGREDIENT_LABEL))
        val sorted = ShoppingAisleOrder.sort(groups, listOf("Frutas", "Lácteos", "Bebidas")).map { it.categoryName }
        assertEquals(listOf("Frutas", "Lácteos", "Bebidas", "Mascotas", UNCATEGORIZED_INGREDIENT_LABEL), sorted)
    }

    @Test
    fun `empty order leaves groups untouched`() {
        val groups = listOf(group("Verduras"), group("Frutas"))
        assertEquals(groups, ShoppingAisleOrder.sort(groups, emptyList()))
    }

    @Test
    fun `order matching ignores case and accents`() {
        val sorted = ShoppingAisleOrder.sort(listOf(group("Frutas"), group("Lácteos")), listOf("lacteos", "FRUTAS")).map { it.categoryName }
        assertEquals(listOf("Lácteos", "Frutas"), sorted)
    }

    @Test
    fun `complete appends missing categories once and always includes uncategorized`() {
        val completed = ShoppingAisleOrder.complete(listOf("Frutas"), listOf("Verduras", "frutas", "Carnes"))
        assertEquals(listOf("Frutas", "Verduras", "Carnes", UNCATEGORIZED_INGREDIENT_LABEL), completed)
    }

    @Test
    fun `move swaps with the neighbour and ignores out of range`() {
        val order = listOf("A", "B", "C")
        assertEquals(listOf("B", "A", "C"), ShoppingAisleOrder.move(order, 1, -1))
        assertEquals(listOf("A", "C", "B"), ShoppingAisleOrder.move(order, 1, 1))
        assertEquals(order, ShoppingAisleOrder.move(order, 0, -1))
        assertEquals(order, ShoppingAisleOrder.move(order, 2, 1))
    }

    @Test
    fun `predefined lists have negative unique ids and parse into items`() {
        val lists = PredefinedShoppingLists.ALL
        assertTrue(lists.all { it.id < 0 })
        assertEquals(lists.size, lists.map { it.id }.toSet().size)
        assertTrue(lists.all { it.items.size >= 5 })
    }
}
