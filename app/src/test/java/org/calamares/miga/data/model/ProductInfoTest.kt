package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductInfoTest {

    @Test
    fun `codec round trips a full product`() {
        val info = ProductInfo(
            barcode = "8410000000000", brand = "Hacendado", quantity = "1 L", nutriScore = "b", nova = 3,
            allergens = listOf("milk"), labels = listOf("organic"), energyKcal = 46.0, proteins = 3.2,
            imageUrl = "https://images.openfoodfacts.org/a.jpg"
        )
        assertEquals(info, ProductInfoCodec.decode(ProductInfoCodec.encode(info)))
    }

    @Test
    fun `decode tolerates garbage, blanks and unknown fields`() {
        assertNull(ProductInfoCodec.decode(null))
        assertNull(ProductInfoCodec.decode("  "))
        assertNull(ProductInfoCodec.decode("no es json"))
        assertEquals("1234567890123", ProductInfoCodec.decode("""{"barcode":"1234567890123","campoFuturo":5}""")?.barcode)
    }

    @Test
    fun `nutri-score colors and letters only for a to e`() {
        assertEquals(0xFF038141, ProductLabels.nutriScoreArgb("a"))
        assertEquals(0xFFE63E11, ProductLabels.nutriScoreArgb("E"))
        assertNull(ProductLabels.nutriScoreArgb("z"))
        assertNull(ProductLabels.nutriScoreArgb(null))
        assertEquals("C", ProductLabels.gradeLetter(" c "))
        assertNull(ProductLabels.gradeLetter("unknown"))
    }

    @Test
    fun `nova descriptions cover 1 to 4`() {
        assertEquals(L10n.str(R.string.ultra_processed), ProductLabels.novaDescription(4))
        assertNull(ProductLabels.novaDescription(7))
        assertNull(ProductLabels.novaArgb(0))
    }

    @Test
    fun `allergen names are translated and unknown ones stay readable`() {
        assertEquals(L10n.str(R.string.milk), ProductLabels.allergenName("milk"))
        assertEquals(L10n.str(R.string.sulphites), ProductLabels.allergenName("sulphur-dioxide-and-sulphites"))
        assertEquals("Kiwi rojo", ProductLabels.allergenName("kiwi-rojo"))
    }

    @Test
    fun `badges merge labels and analysis without repeats and skip unknown ones`() {
        val info = ProductInfo("x", labels = listOf("organic", "vegan", "algo-raro"), analysis = listOf("vegan", "palm-oil-free"))
        assertEquals(listOf(L10n.str(R.string.organic), L10n.str(R.string.vegan), L10n.str(R.string.palm_oil_free)), ProductLabels.badges(info))
    }

    @Test
    fun `nutrition rows list only the available values`() {
        val rows = ProductLabels.nutritionRows(ProductInfo("x", energyKcal = 250.0, sugars = 4.5))
        assertEquals(listOf(L10n.str(R.string.energy) to "250 kcal", L10n.str(R.string.which_sugars) to "4.5 g"), rows)
        assertTrue(ProductLabels.nutritionRows(ProductInfo("x")).isEmpty())
    }
}
