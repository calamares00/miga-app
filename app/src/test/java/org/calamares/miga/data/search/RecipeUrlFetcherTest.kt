package org.calamares.miga.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeUrlFetcherTest {

    @Test
    fun `tags scripts and styles are removed and entities decoded`() {
        val html = """<html><head><style>p{color:red}</style><script>var a = "<b>";</script></head>
            <body><h1>Tortilla de patatas</h1><!-- ad --><p>4 huevos &amp; 2 patatas, 1&frac12; cebolla, a&ntilde;ade sal &#8211; &#xF3;k</p></body></html>"""
        assertEquals(
            "Tortilla de patatas 4 huevos & 2 patatas, 1½ cebolla, añade sal – ók",
            RecipeUrlFetcher.extractReadableText(html)
        )
    }

    @Test
    fun `unknown entities are left untouched`() {
        assertEquals("a &foo; b", RecipeUrlFetcher.extractReadableText("a &foo; b"))
    }

    @Test
    fun `charset comes from the header, then the meta tag, then defaults to utf-8`() {
        val latin = "<meta charset=\"ISO-8859-1\">".toByteArray()
        assertEquals(Charsets.ISO_8859_1, RecipeUrlFetcher.charsetOf("text/html; charset=iso-8859-1", ByteArray(0)))
        assertEquals(Charsets.ISO_8859_1, RecipeUrlFetcher.charsetOf("text/html", latin))
        assertEquals(Charsets.UTF_8, RecipeUrlFetcher.charsetOf(null, "<p>hola</p>".toByteArray()))
        assertEquals(Charsets.UTF_8, RecipeUrlFetcher.charsetOf("text/html; charset=bogus-charset", ByteArray(0)))
    }
}
