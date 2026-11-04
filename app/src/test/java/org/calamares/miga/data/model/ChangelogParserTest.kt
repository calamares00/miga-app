package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogParserTest {

    @Test
    fun `the version line becomes the title and is not repeated`() {
        val entry = ChangelogParser.parse(204, "Novedades de la versión 1.0.6:\n• Lista reorganizada.\n• Catálogo con buscador.\n")
        assertEquals("1.0.6", entry.versionName)
        assertEquals("1.0.6 (204)", entry.title)
        assertEquals(listOf("Lista reorganizada.", "Catálogo con buscador."), entry.items)
    }

    @Test
    fun `english notes work the same`() {
        val entry = ChangelogParser.parse(205, "What's new in 1.0.7:\n• Favourites are yours alone.")
        assertEquals("1.0.7 (205)", entry.title)
        assertEquals(listOf("Favourites are yours alone."), entry.items)
    }

    @Test
    fun `notes without a version line keep every line`() {
        val entry = ChangelogParser.parse(194, "First public release of Miga.\n• Recipe books.")
        assertNull(entry.versionName)
        assertEquals("(194)", entry.title)
        assertEquals(listOf("First public release of Miga.", "Recipe books."), entry.items)
    }

    @Test
    fun `a line that only mentions a version is not taken as the header`() {
        val entry = ChangelogParser.parse(210, "• Requires Android 8.0 or later.")
        assertNull(entry.versionName)
        assertEquals(listOf("Requires Android 8.0 or later."), entry.items)
    }
}
