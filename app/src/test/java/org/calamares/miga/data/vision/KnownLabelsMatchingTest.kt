package org.calamares.miga.data.vision

import org.calamares.miga.data.ai.KnownLabels
import org.calamares.miga.data.repository.DefaultCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownLabelsMatchingTest {

    private val known = KnownLabels(listOf("Postres", "Sopas y cremas"), listOf("Horno", "Freidora de aire", "Thermomix"))

    @Test
    fun `AI labels are filed under the existing ones`() {
        val recipe = RecipeVisionResultDto(name = "Flan", categoryName = "Postre, Clásicos", utensils = listOf("horno", "Airfryer", "Mandolina"))
            .matchedTo(known)
        assertEquals("Postres, Clásicos", recipe.categoryName)
        assertEquals(listOf("Horno", "Freidora de aire", "Mandolina"), recipe.utensils)
    }

    @Test
    fun `only what does not exist yet is reported as new`() {
        val labels = newLabels("Bebidas", listOf("Horno", "Mandolina"), known)
        assertEquals("Bebidas", labels.category)
        assertEquals(listOf("Mandolina"), labels.equipment)
        assertTrue(newLabels("postres", listOf("horno"), known).isEmpty)
        assertNull(newLabels(null, emptyList(), known).category)
    }

    @Test
    fun `defaults are recognised in both languages and old versions`() {
        assertTrue(DefaultCatalog.isDefaultCategory("Sopas y cremas"))
        assertTrue(DefaultCatalog.isDefaultCategory("Cremas"))
        assertTrue(DefaultCatalog.isDefaultCategory("desserts"))
        assertFalse(DefaultCatalog.isDefaultCategory("Recetas de la abuela"))
        assertTrue(DefaultCatalog.isDefaultEquipment("Freidora de aire"))
        assertTrue(DefaultCatalog.isDefaultEquipment("Air fryer"))
        assertFalse(DefaultCatalog.isDefaultEquipment("Thermomix TM31"))
    }
}
