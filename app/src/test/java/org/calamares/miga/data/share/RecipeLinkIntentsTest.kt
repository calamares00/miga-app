package org.calamares.miga.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which shared texts are a recipe to import and which stay shopping list items. */
class RecipeLinkIntentsTest {

    @Test
    fun `an instagram link, alone or with text around`() {
        val link = "https://www.instagram.com/reel/Dc1WSDvM7Kt/?igsh=MXI5dnIy"
        assertEquals(link, RecipeLinkIntents.recipeLinkIn(link))
        assertEquals(link, RecipeLinkIntents.recipeLinkIn("Mira esta receta: $link."))
    }

    @Test
    fun `a page shared from the browser, with its title`() {
        assertEquals(
            "https://www.recetasdeprueba.es/tortilla",
            RecipeLinkIntents.recipeLinkIn("Tortilla de patatas jugosa - Recetas de prueba\nhttps://www.recetasdeprueba.es/tortilla")
        )
    }

    @Test
    fun `lists and notes stay shopping items`() {
        assertNull(RecipeLinkIntents.recipeLinkIn("leche\n2 kg de tomates\nhuevos"))
        assertNull(RecipeLinkIntents.recipeLinkIn("https://a.example/1 y https://b.example/2"))
        assertNull(RecipeLinkIntents.recipeLinkIn("${"pan, leche, huevos, ".repeat(20)} https://tienda.example/oferta"))
    }
}
