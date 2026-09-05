package com.bmo00.miga.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PacksCatalogClientTest {

    @Test
    fun `builds a raw githubusercontent url from an owner slash repo path`() {
        val url = PacksCatalogClient.catalogUrlFor("bmo00/miga-packs")
        assertEquals("https://raw.githubusercontent.com/bmo00/miga-packs/main/catalog.json", url)
    }

    @Test
    fun `trims surrounding slashes and whitespace from a repo path`() {
        val url = PacksCatalogClient.catalogUrlFor("  /bmo00/miga-packs/  ")
        assertEquals("https://raw.githubusercontent.com/bmo00/miga-packs/main/catalog.json", url)
    }
}
