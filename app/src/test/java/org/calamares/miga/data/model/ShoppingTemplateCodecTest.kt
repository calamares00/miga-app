package org.calamares.miga.data.model

import org.calamares.miga.data.share.ShoppingListShareCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingTemplateCodecTest {

    private fun decode(body: String) = ShoppingTemplateCodec.decode(body, ShoppingListShareCodec::fromLines)

    @Test
    fun `products keep photo and product sheet through encode and decode`() {
        val info = ProductInfo(barcode = "8410000000000", brand = "Marca", nutriScore = "b")
        val items = listOf(
            TemplateItem("Leche", 2.0, "l"),
            TemplateItem("Galletas", imageUrl = "https://img/galletas.jpg", info = info)
        )
        val decoded = decode(ShoppingTemplateCodec.encode(items))
        assertEquals(items, decoded)
        assertFalse(decoded[0].isProduct)
        assertTrue(decoded[1].isProduct)
        assertEquals("b", decoded[1].info?.nutriScore)
    }

    @Test
    fun `legacy line format is still read`() {
        val legacy = ShoppingListShareCodec.toLines(listOf(ParsedShoppingEntry("Tomates", 2.0, "kg"), ParsedShoppingEntry("Pan", null, null)))
        val decoded = decode(legacy)
        assertEquals(listOf(TemplateItem("Tomates", 2.0, "kg"), TemplateItem("Pan")), decoded)
        assertNull(decoded[1].info)
    }

    @Test
    fun `blank names are dropped and broken json falls back`() {
        assertEquals(listOf(TemplateItem("Arroz")), decode(ShoppingTemplateCodec.encode(listOf(TemplateItem("  "), TemplateItem("Arroz")))))
        assertEquals(emptyList<TemplateItem>(), decode("[not json"))
    }

    @Test
    fun `upsert replaces an item with the same name instead of duplicating it`() {
        val start = listOf(TemplateItem("Leche"), TemplateItem("Pan"))
        val product = TemplateItem("leche", imageUrl = "https://img/leche.jpg")
        assertEquals(listOf(product, TemplateItem("Pan")), ShoppingTemplateCodec.upsert(start, product))
        assertEquals(start + TemplateItem("Huevos"), ShoppingTemplateCodec.upsert(start, TemplateItem("Huevos")))
    }
}
