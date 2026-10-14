package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class RecipeOriginTest {

    @Test
    fun `flags come from the country code`() {
        assertEquals("🇲🇽", RecipeOrigin.flag("MX"))
        assertEquals("🇪🇸", RecipeOrigin.flag("es"))
        assertNull(RecipeOrigin.flag("XX"))
        assertNull(RecipeOrigin.flag(null))
    }

    @Test
    fun `the country is guessed from adjectives, places and country names`() {
        assertEquals("MX", RecipeOrigin.guessCountry("Mexicana"))
        assertEquals("ES", RecipeOrigin.guessCountry("Córdoba"))
        assertEquals("ES", RecipeOrigin.guessCountry("País Vasco"))
        assertEquals("IN", RecipeOrigin.guessCountry("Cocina india"))
        assertEquals("JP", RecipeOrigin.guessCountry("Japón"))
        assertEquals("TH", RecipeOrigin.guessCountry("Thailand"))
        assertEquals("ES", RecipeOrigin.guessCountry("Granada"))
        assertNull(RecipeOrigin.guessCountry("Receta de la abuela"))
        assertNull(RecipeOrigin.guessCountry(" "))
    }

    @Test
    fun `label shows the flag with the text or the country name`() {
        assertEquals("🇪🇸 Córdoba", RecipeOrigin.label("Córdoba", "ES", Locale("es")))
        assertEquals("🇯🇵 Japón", RecipeOrigin.label(null, "JP", Locale("es")))
        assertEquals("Casera", RecipeOrigin.label("Casera", null, Locale("es")))
        assertNull(RecipeOrigin.label(" ", null, Locale("es")))
    }
}
