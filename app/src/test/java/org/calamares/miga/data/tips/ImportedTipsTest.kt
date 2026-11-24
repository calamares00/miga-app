package org.calamares.miga.data.tips

import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.KnownLabels
import org.calamares.miga.data.ai.TIP_INSTEAD_OF_RECIPE
import org.calamares.miga.data.search.DishSuggestion
import org.calamares.miga.data.search.SocialPost
import org.calamares.miga.data.search.YouTubeVideo
import org.calamares.miga.data.search.buildDishRecipePrompt
import org.calamares.miga.data.search.buildPostAudioPrompt
import org.calamares.miga.data.search.buildUrlImportPrompt
import org.calamares.miga.data.search.buildYouTubePrompt
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.parseRecipeAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tip found by the assistant instead of a recipe, and how it is kept in the tips library. */
class ImportedTipsTest {

    private fun parse(text: String) = parseRecipeAnswer(AiText.Success(text)) { "empty" }

    @Test
    fun `an answer with a tip and no recipe is a tip`() {
        val result = parse(
            """{"name":"","tip":{"category":"PRESERVING","title":"Hierbas frescas más días","body":"Envuelve el perejil en papel de cocina húmedo y guárdalo en un táper en la nevera.","keywords":["perejil","hierbas"]}}"""
        )
        val tip = (result as RecipeVisionResult.Tip).tip
        assertEquals("PRESERVING", tip.category)
        assertEquals("Hierbas frescas más días", tip.title)
        assertEquals(listOf("perejil", "hierbas"), tip.keywords)
    }

    @Test
    fun `a recipe wins over a tip, and an empty tip is no tip`() {
        assertTrue(parse("""{"name":"Tortilla","tip":{"title":"x","body":"y"}}""") is RecipeVisionResult.Success)
        assertEquals(RecipeVisionResult.Error("empty"), parse("""{"name":"","tip":{"title":"","body":""}}"""))
        assertEquals(RecipeVisionResult.Error("empty"), parse("""{"name":""}"""))
    }

    @Test
    fun `importing a link may return a tip, writing a dish's recipe never does`() {
        val post = SocialPost("Truco", null, "@cocina", "https://www.instagram.com/cocina/reel/X/", "Instagram")
        assertTrue(buildUrlImportPrompt("https://example.com", "texto").contains(TIP_INSTEAD_OF_RECIPE.trim()))
        assertTrue(buildPostAudioPrompt(post, transcript = "hola").contains(TIP_INSTEAD_OF_RECIPE.trim()))
        assertTrue(buildYouTubePrompt(YouTubeVideo("abc", "Truco", "Lila", "", null, null)).contains(TIP_INSTEAD_OF_RECIPE.trim()))
        assertFalse(buildDishRecipePrompt(DishSuggestion("Paella", "", null), KnownLabels.NONE).contains("\"tip\""))
    }

    @Test
    fun `an imported tip is tidied, marked and kept with its source`() {
        val tip = ImportedTip.create("preserving ", "  Hierbas frescas  ", "Envuélvelas en papel húmedo.", listOf(" perejil ", ""), "[web](https://example.com)", now = 5)
        assertTrue(tip.id.startsWith(ImportedTip.ID_PREFIX))
        assertEquals("PRESERVING", tip.category)
        assertEquals("Hierbas frescas", tip.title)
        assertEquals(listOf("perejil"), tip.keywords)
        val shown = tip.toCookingTip()
        assertTrue(shown.imported)
        assertEquals(TipCategory.PRESERVING, shown.category)
        assertEquals("[web](https://example.com)", shown.source)
        assertEquals(TipCategory.KITCHEN, ImportedTip.create("NOT_A_CATEGORY", "t", "b", emptyList(), "").toCookingTip().category)
    }

    @Test
    fun `imported tips survive being written and read, and bad data reads as none`() {
        val tips = listOf(ImportedTip.create("BAKING", "Masa", "Deja reposar la masa.", listOf("masa"), "", now = 1))
        assertEquals(tips, ImportedTip.decode(ImportedTip.encode(tips)))
        assertEquals(emptyList<ImportedTip>(), ImportedTip.decode("not json"))
        assertEquals(emptyList<ImportedTip>(), ImportedTip.decode(null))
    }
}
