package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Source" of recipes from posts: "[@user](profile) - [reel](post)". */
class SourceTextTest {

    private val reel = "https://www.instagram.com/poesiadefogon/reel/Dc1WSDvM7Kt/"
    private val profile = "https://www.instagram.com/poesiadefogon/"

    @Test
    fun `author and post as links`() {
        assertEquals("[@poesiadefogon]($profile) - [reel]($reel)", SourceText.post("@poesiadefogon", profile, listOf("reel" to reel)))
        assertEquals("La cocina - [vídeo](https://youtu.be/x)", SourceText.post("La cocina", null, listOf("vídeo" to "https://youtu.be/x")))
    }

    @Test
    fun `the post is the link to copy`() {
        assertEquals(reel, SourceText.mainLink("[@poesiadefogon]($profile) - [reel]($reel)"))
        assertEquals("https://a.es/b", SourceText.mainLink("Libro y https://a.es/b"))
        assertNull(SourceText.mainLink("Libro de la abuela, pág. 12"))
    }

    @Test
    fun `plain text for pdfs and exports`() {
        assertEquals("@poesiadefogon - reel ($reel)", SourceText.plain("[@poesiadefogon]($profile) - [reel]($reel)"))
        assertEquals("Libro de la abuela", SourceText.plain("Libro de la abuela"))
    }
}
