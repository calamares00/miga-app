package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadableQuantityTest {
    @Test
    fun `halves and quarters as fractions, other decimals with the language's separator`() {
        assertEquals("½", readableQuantity(0.5, "es"))
        assertEquals("1½", readableQuantity(1.5, "es"))
        assertEquals("¼", readableQuantity(0.25, "en"))
        assertEquals("2¾", readableQuantity(2.75, "es"))
        assertEquals("1,2", readableQuantity(1.2, "es"))
        assertEquals("1.2", readableQuantity(1.2, "en"))
        assertEquals("400", readableQuantity(400.0, "es"))
    }

    @Test
    fun `an ingredient for reading in the recipe`() {
        assertEquals("½ vaso de vino tinto", formatIngredientText("vino tinto", 0.5, "vaso", language = "es", forReading = true))
        assertEquals("1,5 kg de patatas".replace("1,5", "1½"), formatIngredientText("patatas", 1.5, "kg", language = "es", forReading = true))
        assertEquals("0.5 vaso de vino tinto", formatIngredientText("vino tinto", 0.5, "vaso", language = "es"))
        assertEquals("2 tazas de harina", formatIngredientText("harina", 2.0, "taza", language = "es", forReading = true))
    }
}
