package org.calamares.miga.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PacksCatalogClientTest {

    @Test
    fun `github repo builds raw catalog url`() {
        assertEquals("https://raw.githubusercontent.com/ana/recetas/main/catalog.json", PacksCatalogClient.catalogUrlFor("ana/recetas"))
        assertEquals("https://raw.githubusercontent.com/ana/recetas/main/catalog.json", PacksCatalogClient.catalogUrlFor("  /ana/recetas/  "))
    }

    @Test
    fun `web catalog urls are used as given or get catalog json appended`() {
        assertEquals(DEFAULT_PACKS_CATALOG, PacksCatalogClient.catalogUrlFor(DEFAULT_PACKS_CATALOG))
        assertEquals("https://example.org/packs/catalog.json", PacksCatalogClient.catalogUrlFor("https://example.org/packs/"))
        assertEquals("https://example.org/packs/catalog.json", PacksCatalogClient.catalogUrlFor("https://example.org/packs"))
    }

    @Test
    fun `pack urls resolve relative to the catalog`() {
        val catalog = "https://example.org/packs/catalog.json"
        assertEquals("https://example.org/packs/zips/tapas.zip", PacksCatalogClient.resolveUrl(catalog, "zips/tapas.zip"))
        assertEquals("https://example.org/covers/tapas.jpg", PacksCatalogClient.resolveUrl(catalog, "/covers/tapas.jpg"))
        assertEquals("https://cdn.example.com/a.zip", PacksCatalogClient.resolveUrl(catalog, "https://cdn.example.com/a.zip"))
    }
}
