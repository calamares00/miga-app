package org.calamares.miga.data.search

import kotlinx.serialization.json.Json
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.data.ai.GeminiFileData
import org.calamares.miga.data.ai.GeminiGenerationConfig
import org.calamares.miga.data.ai.GeminiPart
import org.calamares.miga.data.ai.aiJson
import org.calamares.miga.data.share.RecipeLinkIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** YouTube videos as recipe sources: links, the watch page, the description and Gemini. */
class YouTubeRecipeTest {

    private val id = "AbC-123_xyZ"

    @Test
    fun `every kind of youtube link`() {
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=30",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://youtube.com/live/$id?feature=share",
            "https://www.youtube-nocookie.com/embed/$id"
        ).forEach { assertEquals(it, id, YouTubeRecipe.videoId(it)) }
        listOf(
            "https://www.youtube.com/@lacocina",
            "https://www.youtube.com/watch?v=short",
            "https://www.example.com/watch?v=$id",
            "https://www.instagram.com/reel/$id/"
        ).forEach { assertNull(it, YouTubeRecipe.videoId(it)) }
        assertEquals("https://www.youtube.com/watch?v=$id", YouTubeRecipe.watchUrl(id))
    }

    private fun page(description: String) = """
        <html><head><meta property="og:image" content="https://i.ytimg.com/vi/$id/maxresdefault.jpg"></head><body>
        <script>var ytInitialData = {"x": 1};</script>
        <script nonce="n">var ytInitialPlayerResponse = {"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"$id",
        "title":"BIZCOCHO DE YOGUR FÁCIL","lengthSeconds":"312","author":"La cocina de prueba",
        "shortDescription":${Json.encodeToString(description)},
        "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/$id/default.jpg"}]}},
        "microformat":{"playerMicroformatRenderer":{"ownerProfileUrl":"http://www.youtube.com/@cocinadeprueba"}}};var meta = {"a": "}"};</script>
        </body></html>
    """.trimIndent()

    @Test
    fun `reads the video from its watch page, braces and quotes in strings included`() {
        val video = YouTubeRecipe.read(page("Una receta \"de siempre\" {con llaves}\nhttps://www.cocinadeprueba.es/bizcocho-yogur/"), id)!!
        assertEquals("BIZCOCHO DE YOGUR FÁCIL", video.title)
        assertEquals("La cocina de prueba", video.author)
        assertEquals(312, video.seconds)
        assertEquals("https://i.ytimg.com/vi/$id/maxresdefault.jpg", video.thumbnailUrl)
        assertTrue(video.description!!.contains("\"de siempre\" {con llaves}"))
        assertNull("another video's page", YouTubeRecipe.read(page("x"), "ZZZZZZZZZZZ"))
        assertNull(YouTubeRecipe.read("<html>consent</html>", id))
    }

    @Test
    fun `a description written as a recipe needs no assistant`() {
        val description = """
            El bizcocho más fácil del mundo.

            INGREDIENTES (8 raciones):
            - 1 yogur natural
            - 3 huevos
            - 200 g de harina

            ELABORACIÓN:
            1. Mezclamos el yogur con los huevos.
            2. Añadimos la harina y horneamos 35 minutos.

            0:00 Intro
            #bizcocho #receta
        """.trimIndent()
        val video = YouTubeRecipe.read(page(description), id)!!
        val recipe = SocialPostRecipe.parse(YouTubeRecipe.asPost(video))!!.recipe
        assertEquals("Bizcocho de yogur fácil", recipe.name)
        assertEquals(8, recipe.servings)
        assertEquals(3, recipe.ingredientGroups.single().ingredients.size)
        assertEquals(2, recipe.stepGroups.first().instructions.size)
        assertEquals(video.url, recipe.source)
        assertFalse(recipe.notes.contains("La cocina de prueba"))
        val post = YouTubeRecipe.asPost(video)
        assertEquals("@cocinadeprueba", post.author)
        assertEquals("https://www.youtube.com/@cocinadeprueba", post.authorUrl)
    }

    @Test
    fun `links to the recipe on the cook's website, not to networks or shops`() {
        val description = """
            Receta completa: https://www.cocinadeprueba.es/bizcocho-yogur/.
            Suscríbete → https://goo.gl/abc
            Instagram → https://www.instagram.com/cocinadeprueba
            Mi web: https://www.cocinadeprueba.es/
            Molde → https://amzn.to/xyz
            Otra: https://blog.recetas.example/tarta?utm=yt
        """.trimIndent()
        assertEquals(
            listOf("https://www.cocinadeprueba.es/bizcocho-yogur/", "https://blog.recetas.example/tarta?utm=yt"),
            YouTubeRecipe.recipePageLinks(description)
        )
        assertTrue(YouTubeRecipe.recipePageLinks(null).isEmpty())
    }

    @Test
    fun `oembed gives title, channel and thumbnail`() {
        val video = YouTubeRecipe.readOEmbed("""{"title":"Lentejas","author_name":"Lila","author_url":"https://www.youtube.com/@lacocinadelila","thumbnail_url":"https://i.ytimg.com/vi/$id/hqdefault.jpg"}""", id)!!
        assertEquals("Lentejas", video.title)
        assertEquals("Lila", video.author)
        assertEquals("@lacocinadelila", video.handle)
        assertNull(video.description)
    }

    @Test
    fun `gemini gets the video by its address, with low media resolution`() {
        val part = aiJson.encodeToString(GeminiPart.serializer(), GeminiPart(fileData = GeminiFileData("https://www.youtube.com/watch?v=$id")))
        assertEquals("""{"file_data":{"file_uri":"https://www.youtube.com/watch?v=$id"}}""", part)
        val config = aiJson.encodeToString(GeminiGenerationConfig.serializer(), GeminiGenerationConfig(mediaResolution = "MEDIA_RESOLUTION_LOW"))
        assertTrue(config, config.contains("\"mediaResolution\":\"MEDIA_RESOLUTION_LOW\""))
        assertFalse(aiJson.encodeToString(GeminiGenerationConfig.serializer(), GeminiGenerationConfig()).contains("mediaResolution"))
        assertTrue(AiCandidate(AiProvider.GEMINI, "k", "m").watchesYouTube)
        assertFalse(AiCandidate(AiProvider.OPENAI, "k", "m").watchesYouTube)
        val video = YouTubeVideo(id, "Lentejas", "Lila", "Receta en mi web", null, null)
        assertTrue(buildYouTubePrompt(video).contains("Receta en mi web"))
    }

    @Test
    fun `a youtube link shared with text is a recipe to import`() {
        assertEquals("https://youtu.be/$id?si=abc", RecipeLinkIntents.recipeLinkIn("Mira esta receta https://youtu.be/$id?si=abc"))
        assertEquals("https://www.youtube.com/shorts/$id", RecipeLinkIntents.recipeLinkIn("https://www.youtube.com/shorts/$id"))
    }
}
