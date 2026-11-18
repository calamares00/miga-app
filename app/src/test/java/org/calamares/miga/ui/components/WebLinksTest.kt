package org.calamares.miga.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLinksTest {

    private fun links(text: String) =
        withBareLinks(richAnnotatedString(text), Color.Blue).let { annotated ->
            annotated.getStringAnnotations(0, annotated.length).map { it.item }
        }

    @Test
    fun `bare addresses become links without the punctuation after them`() {
        assertEquals(listOf("https://www.shoothecook.es/receta-carrot-cake-facil/"), links("https://www.shoothecook.es/receta-carrot-cake-facil/"))
        assertEquals(listOf("https://example.com/a?b=1"), links("Visto en https://example.com/a?b=1."))
        assertEquals(listOf("http://example.com/x"), links("(http://example.com/x)"))
    }

    @Test
    fun `written links are not linked twice`() {
        assertEquals(listOf("https://example.com"), links("📷 Foto: [Ana](https://example.com) · CC BY"))
    }

    @Test
    fun `text without addresses stays as it is`() {
        assertEquals(AnnotatedString("Libro de la abuela"), withBareLinks(AnnotatedString("Libro de la abuela"), Color.Blue))
        assertNull(firstWebLink("Libro de la abuela, pág. 12"))
        assertEquals("https://a.es/b", firstWebLink("Libro y https://a.es/b"))
    }
}
