package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMatchingTest {

    private val categories = listOf("Entrantes", "Sopas y cremas", "Arroces", "Postres", "Pescados y mariscos", "Carnes")

    @Test
    fun `same words ignoring case, accents and plural`() {
        assertEquals("Postres", CatalogMatching.bestMatch("postre", categories))
        assertEquals("Arroces", CatalogMatching.bestMatch("Arroz", categories))
        assertEquals("Carnes", CatalogMatching.bestMatch("CARNE", categories))
    }

    @Test
    fun `a category that contains the name or is contained in it`() {
        assertEquals("Sopas y cremas", CatalogMatching.bestMatch("Cremas", categories))
        assertEquals("Pescados y mariscos", CatalogMatching.bestMatch("Pescado", categories))
        assertEquals("Postres", CatalogMatching.bestMatch("Postres y dulces", categories))
    }

    @Test
    fun `nothing fits or it is ambiguous`() {
        assertNull(CatalogMatching.bestMatch("Bebidas", categories))
        assertNull(CatalogMatching.bestMatch("Sopa de pescado", categories))
        assertNull(CatalogMatching.bestMatch("Pescado", listOf("Pescado al horno", "Pescado frito")))
    }
}
