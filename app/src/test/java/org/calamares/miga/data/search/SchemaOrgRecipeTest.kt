package org.calamares.miga.data.search

import kotlinx.serialization.json.JsonPrimitive
import org.calamares.miga.data.export.IngredientDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** schema.org Recipe as recipe websites embed it (JSON-LD), in its usual shapes. */
class SchemaOrgRecipeTest {

    private fun page(jsonLd: String) = """
        <html><head><title>Receta</title>
        <script type="application/ld+json">{"@context":"https://schema.org","@type":"WebSite","name":"Blog"}</script>
        <script type='application/ld+json'>$jsonLd</script>
        </head><body><p>Texto</p></body></html>
    """.trimIndent()

    @Test
    fun `a recipe inside a graph with sections, times, servings and photo`() {
        val html = page(
            """
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebPage","name":"Página"},
              {"@type":["Recipe","NewsArticle"],"name":"Tortilla de patatas &amp; cebolla",
               "description":"<p>La de <b>siempre</b>.</p>",
               "image":[{"@type":"ImageObject","url":"/img/tortilla.jpg"}],
               "recipeYield":["4","4 raciones"],
               "prepTime":"PT15M","cookTime":"PT1H5M",
               "recipeCategory":"Cenas","recipeCuisine":"Española",
               "recipeIngredient":["6 huevos","500 g de patatas","1 cebolla","Sal"],
               "recipeInstructions":[
                 {"@type":"HowToStep","text":"Pela y corta las patatas."},
                 {"@type":"HowToSection","name":"Para el cuajado","itemListElement":[
                   {"@type":"HowToStep","text":"Bate los huevos."},
                   {"@type":"HowToStep","name":"Cuaja","text":"Cuaja a fuego medio."}]}]}
            ]}
            """
        )
        val parsed = SchemaOrgRecipe.parse(html, "https://example.com/recetas/tortilla")!!
        val recipe = parsed.recipe
        assertEquals("Tortilla de patatas & cebolla", recipe.name)
        assertEquals("La de siempre.", recipe.notes)
        assertEquals("https://example.com/img/tortilla.jpg", parsed.imageUrl)
        assertEquals(4, recipe.servings)
        assertEquals(15, recipe.prepTimeMinutes)
        assertEquals(65, recipe.cookTimeMinutes)
        assertEquals("Cenas", recipe.categoryName)
        assertEquals("Española", recipe.origin)
        assertEquals("https://example.com/recetas/tortilla", recipe.source)
        assertEquals(
            listOf(IngredientDto("huevos", 6.0, null), IngredientDto("patatas", 500.0, "g"), IngredientDto("cebolla", 1.0, null), IngredientDto("Sal", null, null)),
            recipe.ingredientGroups.single().ingredients
        )
        assertEquals(listOf(null, "Para el cuajado"), recipe.stepGroups.map { it.name })
        assertEquals(listOf("Pela y corta las patatas."), recipe.stepGroups[0].instructions)
        assertEquals(listOf("Bate los huevos.", "Cuaja a fuego medio."), recipe.stepGroups[1].instructions)
    }

    @Test
    fun `steps as one text, a total time only and a plain image address`() {
        val html = page(
            """
            [{"@type":"Recipe","name":"Gazpacho","image":"https://cdn.example.com/g.jpg","totalTime":"P0DT20M","recipeYield":6,
              "recipeIngredient":["1 kg de tomates","1/2 pepino"],
              "recipeInstructions":"1. Trocea las verduras.\n2. Tritura todo.\n3. Enfría."}]
            """
        )
        val recipe = SchemaOrgRecipe.parse(html, "https://example.com/g")!!.recipe
        assertEquals(20, recipe.cookTimeMinutes)
        assertNull(recipe.prepTimeMinutes)
        assertEquals(6, recipe.servings)
        assertEquals(listOf("Trocea las verduras.", "Tritura todo.", "Enfría."), recipe.stepGroups.single().instructions)
        assertEquals(IngredientDto("pepino", 0.5, null), recipe.ingredientGroups.single().ingredients[1])
    }

    @Test
    fun `pages without a recipe, or with an empty one, give nothing`() {
        assertNull(SchemaOrgRecipe.parse("<html><body>Hola</body></html>", "https://example.com"))
        assertNull(SchemaOrgRecipe.parse(page("""{"@type":"Recipe","name":"Vacía"}"""), "https://example.com"))
        assertNull(SchemaOrgRecipe.parse(page("""{ esto no es json"""), "https://example.com"))
    }

    @Test
    fun `iso durations`() {
        assertEquals(90, SchemaOrgRecipe.minutes(JsonPrimitive("PT1H30M")))
        assertEquals(1440, SchemaOrgRecipe.minutes(JsonPrimitive("P1D")))
        assertNull(SchemaOrgRecipe.minutes(JsonPrimitive("PT0M")))
        assertNull(SchemaOrgRecipe.minutes(JsonPrimitive("30 minutos")))
    }
}
