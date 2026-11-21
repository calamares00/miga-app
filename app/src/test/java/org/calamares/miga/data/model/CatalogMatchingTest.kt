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

    private val catalog = listOf("Limón", "Tomate", "Leche entera", "Leche desnatada", "Leche de coco", "Huevo de gallina", "Clara de huevo", "Aceite de oliva", "Aceite de oliva virgen extra", "Pan rallado")
    private val index = CatalogMatching.Index(catalog) { it }

    @Test
    fun `free shopping names find their catalogue entries`() {
        assertEquals(listOf("Limón"), index.matches("limones"))
        assertEquals(listOf("Tomate"), index.matches("tomates"))
        assertEquals(listOf("Leche entera", "Leche desnatada", "Leche de coco"), index.matches("leche"))
        assertEquals(listOf("Huevo de gallina", "Clara de huevo"), index.matches("huevos"))
        // The most specific entry inside the name wins.
        assertEquals(listOf("Aceite de oliva"), index.matches("aceite de oliva suave"))
        assertEquals(listOf("Aceite de oliva virgen extra"), index.matches("aceite de oliva virgen extra"))
        assertEquals(emptyList<String>(), index.matches("pan de barra"))
    }
}
