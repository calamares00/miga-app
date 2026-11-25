package org.calamares.miga.data.model

import kotlinx.serialization.json.Json
import org.calamares.miga.data.export.BookExportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A book's description, author and the look of a cover without a photo. */
private val lenientJson = Json { ignoreUnknownKeys = true }

class BookDetailsTest {

    private val old = listOf(0xFFC1633D, 0xFF3E8EB5, 0xFF5C9E4B, 0xFF8E6BB5, 0xFFD1709F, 0xFFD9962B, 0xFF3FA39B)

    @Test
    fun `books without a chosen colour keep the one they always had`() {
        listOf("Recetas de la abuela", "Postres", "De diario", "Navidad", "").forEach { name ->
            assertEquals(name, old[Math.floorMod(name.hashCode(), old.size)], BookColor.of(null, name).argb)
        }
        assertEquals(BookColor.WINE, BookColor.of("WINE", "Postres"))
        assertEquals("an unknown colour falls back to the name's", BookColor.of(null, "Postres"), BookColor.of("NEON", "Postres"))
    }

    @Test
    fun `imported details are trimmed and checked`() {
        val details = BookDetails(" ${"a".repeat(400)} ", " Abuela Carmen ", "NEON", "  ").cleaned()
        assertEquals(BookDetails.MAX_DESCRIPTION, details.description.length)
        assertEquals("Abuela Carmen", details.author)
        assertNull(details.coverColor)
        assertNull(details.coverEmoji)
    }

    @Test
    fun `sync keeps what an older copy does not send and clears what is sent empty`() {
        val mine = BookDetails("Para mi hija", "Abuela", "WINE", "🍰")
        assertEquals("an old app or server sends nothing", mine, mine.updatedWith(null, null, null, null))
        assertEquals(BookDetails("", "", null, null), mine.updatedWith("", "", "", ""))
        assertEquals(BookDetails("Nueva", "Abuela", "TEAL", "🍰"), mine.updatedWith("Nueva", null, "TEAL", null))
    }

    @Test
    fun `books exported by older versions read without details`() {
        val dto = lenientJson.decodeFromString(BookExportDto.serializer(), """{"uid":"u","name":"Postres"}""")
        assertEquals(BookDetails(), dto.details)
    }
}
