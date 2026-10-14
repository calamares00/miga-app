package org.calamares.miga.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterParsingTest {

    @Test
    fun `models with the free suffix are free`() {
        assertTrue(isFreeModel("vendor/model:free", null, null))
    }

    @Test
    fun `models priced at zero are free`() {
        assertTrue(isFreeModel("vendor/model", "0", "0"))
    }

    @Test
    fun `paid models are not free`() {
        assertFalse(isFreeModel("vendor/model", "0.000001", "0.000002"))
        assertFalse(isFreeModel("vendor/model", null, null))
    }

    @Test
    fun `json is extracted from surrounding chatter`() {
        assertEquals("""{"a":1}""", extractJsonObject("Claro, aquí tienes:\n```json\n{\"a\":1}\n```\n¡Que aproveche!"))
    }

    @Test
    fun `text without json is returned trimmed`() {
        assertEquals("sin json", extractJsonObject("  sin json "))
    }
}
