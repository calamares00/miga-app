package com.bmo00.miga.data.share

import com.bmo00.miga.data.model.ParsedShoppingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListShareCodecTest {

    @Test
    fun `encode then decode gives the same entries`() {
        val entries = listOf(
            ParsedShoppingEntry("Tomates", 2.0, "kg"),
            ParsedShoppingEntry("Leche", null, null),
            ParsedShoppingEntry("Huevos", 12.0, null),
            ParsedShoppingEntry("Queso", 1.5, "kg")
        )
        assertEquals(entries, ShoppingListShareCodec.decode(ShoppingListShareCodec.encode(entries)))
    }

    @Test
    fun `payload carries the Miga prefix`() {
        assertTrue(ShoppingListShareCodec.encode(listOf(ParsedShoppingEntry("Pan", null, null))).startsWith(ShoppingListShareCodec.PREFIX))
    }

    @Test
    fun `foreign or corrupt text is rejected`() {
        assertNull(ShoppingListShareCodec.decode("https://example.com"))
        assertNull(ShoppingListShareCodec.decode(ShoppingListShareCodec.PREFIX + "no-es-base64-válido!!"))
        assertNull(ShoppingListShareCodec.decode(ShoppingListShareCodec.PREFIX + "AAAA"))
    }

    @Test
    fun `tabs and newlines in names do not break the format`() {
        val decoded = ShoppingListShareCodec.decode(ShoppingListShareCodec.encode(listOf(ParsedShoppingEntry("Pan\ttostado\nrústico", 1.0, null))))
        assertEquals("Pan tostado rústico", decoded?.single()?.name)
    }

    @Test
    fun `lines round trip keeps quantity, unit and name`() {
        val entries = listOf(ParsedShoppingEntry("Tomates", 2.0, "kg"), ParsedShoppingEntry("Leche", null, null))
        assertEquals(entries, ShoppingListShareCodec.fromLines(ShoppingListShareCodec.toLines(entries)))
    }

    @Test
    fun `lines ignore blank and malformed rows`() {
        assertEquals(emptyList<ParsedShoppingEntry>(), ShoppingListShareCodec.fromLines("solo texto\n\t\t"))
    }
}
