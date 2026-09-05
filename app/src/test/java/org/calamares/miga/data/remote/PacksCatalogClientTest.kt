package org.calamares.miga.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PacksCatalogClientTest {

    @Test
    fun `builds a raw githubusercontent url from an owner slash repo path`() {
        val url = PacksCatalogClient.catalogUrlFor("calamares00/miga-packs")
        assertEquals("https://raw.githubusercontent.org/calamares/miga-packs/main/catalog.json", url)
    }

    @Test
    fun `trims surrounding slashes and whitespace from a repo path`() {
        val url = PacksCatalogClient.catalogUrlFor("  /calamares00/miga-packs/  ")
        assertEquals("https://raw.githubusercontent.org/calamares/miga-packs/main/catalog.json", url)
    }
}
