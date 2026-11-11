package org.calamares.miga.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun pack(id: String, language: String?) =
        PackEntryDto(id = id, name = id, author = "Miga", latestVersion = 1, downloadUrl = "$id.zip", language = language)

    @Test
    fun `the catalog lists packs in the app language, packs for every language and installed ones`() {
        val packs = listOf(pack("tapas", "es"), pack("tapas-en", "en"), pack("basics", null), pack("navidad", "es"))
        assertEquals(listOf("tapas", "basics", "navidad"), packs.visibleFor("es", emptySet()).map { it.id })
        assertEquals(listOf("tapas-en", "basics"), packs.visibleFor("en", emptySet()).map { it.id })
        assertEquals(listOf("tapas-en", "basics", "navidad"), packs.visibleFor("EN", setOf("navidad")).map { it.id })
    }

    @Test
    fun `only https catalogues and packs are accepted`() {
        assertTrue(PacksCatalogClient.isHttps("https://example.org/a.zip"))
        assertTrue(PacksCatalogClient.isHttps(" HTTPS://example.org/a.zip"))
        assertFalse(PacksCatalogClient.isHttps("http://example.org/a.zip"))
        assertFalse(PacksCatalogClient.isHttps("ftp://example.org/a.zip"))
        assertEquals(PackDownloadResult.NotHttps, runBlocking { PacksCatalogClient.downloadPackZip("http://example.org/a.zip") })
    }

    @Test
    fun `a pack is checked against the hash of its catalogue, when there is one`() {
        val bytes = "miga".toByteArray()
        val hash = "0f3a2e7f1c36d9d4cd4b6b4a09f1be5f5bd3b1b6d7b5dd36a34ac4a5a3f7b2d1"
        val real = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(PacksCatalogClient.matchesSha256(bytes, null))
        assertTrue(PacksCatalogClient.matchesSha256(bytes, " "))
        assertTrue(PacksCatalogClient.matchesSha256(bytes, real))
        assertTrue(PacksCatalogClient.matchesSha256(bytes, real.uppercase()))
        assertFalse(PacksCatalogClient.matchesSha256(bytes, hash))
        assertFalse(PacksCatalogClient.matchesSha256(bytes + 1, real))
    }
}
