package org.calamares.miga.data.search

import kotlinx.serialization.json.Json
import org.calamares.miga.data.export.IngredientDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recipes written in the caption of an Instagram post, as its public page gives them. */
class SocialPostRecipeTest {

    // Written like the usual recipe reels: emoji headers, "▪️" bullets, keycap step numbers, a tip,
    // dots to push the hashtags down. Entities as Instagram encodes them.
    private val caption = """
        Garbanzos con espinacas y huevo &#x1f95a;&#x1f33f;

        Un plato de cuchara rapid&#xed;simo para cualquier d&#xed;a de diario.

        &#x1f4dd; INGREDIENTES (3 personas):

        &#x25aa;&#xfe0f;400g de garbanzos cocidos
        &#x25aa;&#xfe0f;200g de espinacas frescas 
        &#x25aa;&#xfe0f;2 dientes de ajo
        &#x25aa;&#xfe0f;1 cucharadita de piment&#xf3;n dulce
        &#x25aa;&#xfe0f;3 huevos
        &#x25aa;&#xfe0f;Aceite de oliva y sal

        &#x1f468;&#x1f3fb;&#x200d;&#x1f373; PASO A PASO:

        1&#xfe0f;&#x20e3; Doramos los ajos laminados en una sart&#xe9;n con aceite.
        2&#xfe0f;&#x20e3; A&#xf1;adimos las espinacas y las dejamos reducir.
        Despu&#xe9;s, el piment&#xf3;n sin que se queme.
        3&#xfe0f;&#x20e3; Incorporamos los garbanzos y cocinamos 5 minutos.
        4&#xfe0f;&#x20e3; Cuajamos los huevos encima con la sart&#xe9;n tapada.

        &#x1f538;Si os gusta con un toque &#xe1;cido, un chorrito de vinagre al final.
        .
        .
        #recetas #garbanzos #cocinacasera
    """.trimIndent()

    private fun page(description: String) = """
        <html><head>
        <meta property="og:type" content="article" />
        <meta property="og:site_name" content="Instagram" />
        <meta property="og:url" content="https://www.instagram.com/cocinadeprueba/reel/ABC123/" />
        <meta property="og:image" content="https://scontent.cdninstagram.com/v/foto.jpg?stp=x&amp;oh=y" />
        <meta property="og:description" content="$description" />
        <meta name="twitter:title" content="Cocina de prueba (&#064;cocinadeprueba) &#x2022; Instagram reel" />
        </head><body></body></html>
    """.trimIndent()

    private val url = "https://www.instagram.com/reel/ABC123/?igsh=xyz"

    @Test
    fun `instagram links`() {
        assertTrue(SocialPostRecipe.isInstagram("https://www.instagram.com/reel/Dc1WSDvM7Kt/?igsi=MXI5"))
        assertTrue(SocialPostRecipe.isInstagram("https://instagram.com/p/ABC/"))
        assertTrue(SocialPostRecipe.isInstagram("https://www.instagram.com/poesiadefogon/reel/Dc1WSDvM7Kt/"))
        assertFalse(SocialPostRecipe.isInstagram("https://www.instagram.com/poesiadefogon/"))
        assertFalse(SocialPostRecipe.isInstagram("https://www.example.com/reel/abc"))
    }

    @Test
    fun `reads the caption out of the likes and comments wrapper`() {
        val post = SocialPostRecipe.read(page("21K likes, 232 comments - cocinadeprueba on September 3, 2026: &quot;$caption&quot;."), url)!!
        assertEquals("@cocinadeprueba", post.author)
        assertEquals("https://www.instagram.com/cocinadeprueba/", post.authorUrl)
        assertTrue(SocialPostRecipe.isReel(post.url))
        assertFalse(SocialPostRecipe.isReel("https://www.instagram.com/p/ABC/"))
        assertEquals("https://www.instagram.com/cocinadeprueba/reel/ABC123/", post.url)
        assertEquals("https://scontent.cdninstagram.com/v/foto.jpg?stp=x&oh=y", post.imageUrl)
        assertTrue(post.text.startsWith("Garbanzos con espinacas y huevo"))
        assertFalse("hashtags and dots go", post.text.contains("#recetas") || post.text.lines().any { it.trim() == "." })
    }

    @Test
    fun `the same wrapper in spanish`() {
        val post = SocialPostRecipe.read(page("21 mil Me gusta, 232 comentarios - cocinadeprueba el 3 de septiembre de 2026: &quot;$caption&quot;"), url)!!
        assertEquals("@cocinadeprueba", post.author)
        assertTrue(post.text.startsWith("Garbanzos"))
    }

    @Test
    fun `turns a structured caption into a recipe`() {
        val post = SocialPostRecipe.read(page("1,234 likes, 5 comments - cocinadeprueba on May 1, 2026: &quot;$caption&quot;"), url)!!
        val read = SocialPostRecipe.parse(post)!!
        val recipe = read.recipe
        assertEquals("Garbanzos con espinacas y huevo", recipe.name)
        assertEquals(3, recipe.servings)
        assertEquals(post.url, recipe.source)
        assertEquals(post.imageUrl, read.imageUrl)
        val ingredients = recipe.ingredientGroups.single().ingredients
        assertEquals(6, ingredients.size)
        assertEquals(IngredientDto("garbanzos cocidos", 400.0, "g"), ingredients[0])
        assertEquals(3.0, ingredients[4].quantity)
        val steps = recipe.stepGroups.single().instructions
        assertEquals(4, steps.size)
        assertEquals("Doramos los ajos laminados en una sartén con aceite.", steps[0])
        assertEquals("Añadimos las espinacas y las dejamos reducir. Después, el pimentón sin que se queme.", steps[1])
        assertTrue(recipe.notes.contains("Un plato de cuchara rapidísimo"))
        assertTrue("the tip goes to the notes", recipe.notes.contains("Si os gusta con un toque ácido"))
        assertFalse("the author goes in the source, not in the notes", recipe.notes.contains("@cocinadeprueba"))
    }

    @Test
    fun `a caption without a recipe is left to the assistant`() {
        val post = SocialPostRecipe.read(page("10 likes, 1 comments - cocinadeprueba on May 1, 2026: &quot;Qué rica la comida de hoy en casa de mi abuela #domingo&quot;"), url)!!
        assertNull(SocialPostRecipe.parse(post))
    }

    @Test
    fun `instagram without the caption gives nothing`() {
        assertNull(SocialPostRecipe.read("<html><head><title>Instagram</title></head></html>", url))
    }

    @Test
    fun `other pages only when they say they are a post`() {
        val html = """<meta property="og:type" content="website" /><meta property="og:description" content="${"Una tienda con muchas cosas. ".repeat(5)}" />"""
        assertNull(SocialPostRecipe.read(html, "https://www.example.com/tienda"))
    }

    @Test
    fun `an article of a blog or magazine is a web page, a post of a social network is a post`() {
        val article = """<meta property="og:type" content="article" /><meta property="og:description" content="${"Cuando hacemos un caldo solemos desechar las verduras. ".repeat(3)}" />"""
        assertNull(SocialPostRecipe.read(article, "https://www.directoalpaladar.com/curso-de-cocina/como-mejorar-tu-sopa"))
        assertTrue(SocialPostRecipe.read(article, "https://www.tiktok.com/@cocina/video/123") != null)
        assertTrue(SocialPostRecipe.isSocialNetwork("https://m.facebook.com/cocina/posts/1"))
    }

    private fun post(text: String) = SocialPost(text, null, "@prueba", "https://www.instagram.com/p/X/", "Instagram")

    @Test
    fun `capital titles, emoji bullets and no calls to action`() {
        val recipe = SocialPostRecipe.parse(
            post(
                """
                🍎✨ BIZCOCHO DE YOGUR ✨🍎

                📝 INGREDIENTES

                🥚 3 huevos
                🥛 125 g de yogur
                🌾 200 g de harina

                👩‍🍳 ELABORACIÓN

                1️⃣ Bate los huevos.

                2️⃣ Añade el yogur y la harina.

                💡 TRUCO: Mejor al día siguiente.

                😍 ¿Te animas? Cuéntame en comentarios y guarda esta receta.
                """.trimIndent()
            )
        )!!.recipe
        assertEquals("Bizcocho de yogur", recipe.name)
        assertEquals(listOf("huevos", "yogur", "harina"), recipe.ingredientGroups.single().ingredients.map { it.name })
        assertEquals(listOf("Bate los huevos.", "Añade el yogur y la harina."), recipe.stepGroups.single().instructions)
        assertTrue(recipe.notes.contains("TRUCO: Mejor al día siguiente."))
        assertFalse(recipe.notes.contains("comentarios"))
    }

    @Test
    fun `a post that sends to a website for the recipe`() {
        val text = "Salmón a la naranja 😏\n\n👉 Receta completa con ingredientes y cantidades en mi web: www.cocinadeprueba.com\n\n¡Espero que os guste!"
        assertNull(SocialPostRecipe.parse(post(text)))
        assertEquals("cocinadeprueba.com", SocialPostRecipe.recipeElsewhere(post(text)))
        assertNull(SocialPostRecipe.recipeElsewhere(post("Qué rica la comida de hoy")))
        assertNull(SocialPostRecipe.recipeElsewhere(post(caption.replace("&#x1f4dd; ", "") + "\nMás en cocinadeprueba.com")))
    }

    private fun embedPage(caption: String) = Json.encodeToString(
        """{"context":{"type":"media"},"gql_data":{"shortcode_media":{"__typename":"GraphVideo","is_video":true,""" +
            """"display_url":"https://instagram.fmad10-2.fna.fbcdn.net/v/foto_grande.jpg?stp=dst-jpg_e15_tt6&oh=a",""" +
            """"video_url":"https://instagram.fmad8-1.fna.fbcdn.net/o1/v/video.mp4?oh=b",""" +
            """"owner":{"username":"cocinadeprueba"},""" +
            """"edge_media_to_caption":{"edges":[{"node":{"text":${Json.encodeToString(caption)}}}]}}}}"""
    ).let { """<html><script>s.handle({"require":[["Embed","init",null,[{"contextJSON":$it,"isAd":false}]]]});</script></html>""" }

    @Test
    fun `the embed page gives the photo without the play button`() {
        assertEquals("https://www.instagram.com/p/ABC123/embed/captioned/", SocialPostRecipe.instagramEmbedUrl("https://www.instagram.com/reel/ABC123/?igsh=x"))
        assertNull(SocialPostRecipe.instagramEmbedUrl("https://www.example.com/reel/ABC123/"))
        val embed = SocialPostRecipe.readInstagramEmbed(embedPage("Garbanzos\n\nINGREDIENTES:\n- 400 g de garbanzos"))!!
        assertEquals("https://instagram.fmad10-2.fna.fbcdn.net/v/foto_grande.jpg?stp=dst-jpg_e15_tt6&oh=a", embed.imageUrl)
        assertEquals("https://instagram.fmad8-1.fna.fbcdn.net/o1/v/video.mp4?oh=b", embed.videoUrl)
        assertEquals("@cocinadeprueba", embed.author)
        assertTrue(embed.caption!!.startsWith("Garbanzos\n"))

        val fromPage = SocialPostRecipe.read(page("1 likes, 1 comments - cocinadeprueba on May 1, 2026: &quot;$caption&quot;"), url)!!
        val merged = SocialPostRecipe.withEmbed(fromPage, embed, url)!!
        assertEquals(embed.imageUrl, merged.imageUrl)
        assertEquals(fromPage.text, merged.text)
    }

    @Test
    fun `the embed page alone, when the post page hides the caption`() {
        val embed = SocialPostRecipe.readInstagramEmbed(embedPage("Garbanzos\n.\n#recetas"))
        val post = SocialPostRecipe.withEmbed(null, embed, "https://www.instagram.com/reel/ABC123/?igsh=x")!!
        assertEquals("Garbanzos", post.text)
        assertEquals("https://www.instagram.com/reel/ABC123/", post.url)
        assertNull(SocialPostRecipe.readInstagramEmbed("<html>Sin datos</html>"))
    }
}
