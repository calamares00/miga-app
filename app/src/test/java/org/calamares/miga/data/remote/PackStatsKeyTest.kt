package org.calamares.miga.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PackStatsKeyTest {

    private fun entry(id: String, family: String?) =
        PackEntryDto(id = id, name = id, author = "Miga", latestVersion = 1, downloadUrl = "x.zip", family = family)

    @Test
    fun `both languages of a pack share its family counter`() {
        assertEquals("cocina-india", entry("cocina-india", "cocina-india").statsKey)
        assertEquals("cocina-india", entry("indian-cooking", "cocina-india").statsKey)
    }

    @Test
    fun `packs without a family are counted by id`() {
        assertEquals("my-pack", entry("my-pack", null).statsKey)
        assertEquals("my-pack", entry("my-pack", " ").statsKey)
    }
}
