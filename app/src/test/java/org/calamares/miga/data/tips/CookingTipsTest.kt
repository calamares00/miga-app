package org.calamares.miga.data.tips

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.searchKey
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The bundled tips file, and how tips are matched to recipes, searched and picked for each day. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class CookingTipsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val spanish = CookingTips.all(context, "es")
    private val english = CookingTips.all(context, "en")

    private fun recipe(
        name: String,
        ingredients: List<String> = emptyList(),
        steps: List<String> = emptyList(),
        utensils: List<String> = emptyList()
    ) = Recipe(
        uid = "uid", recipeBookId = 1L, recipeBookName = "Libro", name = name, categoryId = null, categoryName = null,
        difficulty = Difficulty.EASY, prepTimeMinutes = null, cookTimeMinutes = null, servings = 4, notes = "", source = "",
        isFavorite = false, timesCooked = 0, createdAt = 0L, updatedAt = 0L, photos = emptyList(),
        ingredientGroups = listOf(IngredientGroup(null, ingredients.map { Ingredient(it, null, null) })),
        stepGroups = listOf(StepGroup(null, steps)), tags = emptyList(), utensils = utensils
    )

    private fun ids(tips: List<CookingTip>) = tips.map { it.id }

    @Test
    fun `both languages have the same tips, every category is used and texts are short`() {
        assertTrue(spanish.size >= 60)
        assertEquals(ids(spanish), ids(english))
        assertEquals(spanish.map { it.category }, english.map { it.category })
        assertEquals(spanish.size, ids(spanish).toSet().size)
        assertEquals(TipCategory.entries.toSet(), spanish.map { it.category }.toSet())
        (spanish + english).forEach { tip ->
            assertTrue(tip.id, tip.title.isNotBlank() && tip.title.length <= 60)
            assertTrue(tip.id, tip.body.isNotBlank() && tip.body.length <= 400)
        }
        // Keywords of both languages, already without accents, so either finds the tip.
        val aubergine = spanish.first { it.id == "aubergine-salt" }
        assertTrue("berenjena" in aubergine.keywords && "eggplant" in aubergine.keywords)
        spanish.flatMap { it.keywords }.forEach { assertEquals(searchKey(it), it) }
    }

    @Test
    fun `a recipe gets the tips of its ingredients, plurals included, best first`() {
        val tips = tipsForRecipe(
            recipe("Berenjenas rellenas", ingredients = listOf("berenjenas", "carne picada", "tomate triturado"), steps = listOf("Hornea 20 minutos.")),
            spanish
        )
        assertEquals("aubergine-salt", tips.first().id)
        assertTrue(tips.size <= 3)
    }

    @Test
    fun `keywords match whole words only`() {
        // "pan" (bread) must not match "panceta", nor "ajo" match "trabajo".
        val tips = tipsForRecipe(recipe("Lentejas", ingredients = listOf("panceta"), steps = listOf("Un trabajo sencillo.")), spanish)
        assertFalse("freeze-bread" in ids(tips))
        assertFalse("peel-garlic" in ids(tips))
    }

    @Test
    fun `an English recipe finds its tips with the app in Spanish`() {
        val tips = tipsForRecipe(recipe("Spaghetti carbonara", ingredients = listOf("spaghetti", "egg yolks", "guanciale")), spanish)
        assertTrue("pasta-water" in ids(tips))
        assertEquals("Guarda el agua de la pasta", tips.first { it.id == "pasta-water" }.title)
    }

    @Test
    fun `equipment counts and general tips are never attached to recipes`() {
        val tips = tipsForRecipe(recipe("Alitas", ingredients = listOf("alitas de pollo"), utensils = listOf("Freidora de aire")), spanish)
        assertTrue("air-fryer" in ids(tips))
        val general = spanish.filter { it.keywords.isEmpty() }.map { it.id }.toSet()
        assertTrue(general.isNotEmpty())
        val everything = recipe("Todo", ingredients = spanish.flatMap { it.keywords }, steps = general.toList())
        assertTrue(tipsForRecipe(everything, spanish, limit = spanish.size).none { it.id in general })
    }

    @Test
    fun `search ignores accents and case`() {
        assertTrue("aubergine-salt" in ids(searchTips(spanish, "BERENJENA")))
        assertTrue("peel-tomatoes" in ids(searchTips(spanish, "pelar tomates")))
        assertTrue("onion-no-tears" in ids(searchTips(spanish, "cebolla sin llorar")))
        assertEquals(spanish, searchTips(spanish, "  "))
    }

    @Test
    fun `the tip of the day goes through every tip before repeating`() {
        val start = LocalDate.of(2026, 11, 14)
        val days = spanish.indices.map { tipOfTheDay(spanish, start.plusDays(it.toLong()))!!.id }
        assertEquals(spanish.size, days.toSet().size)
        assertEquals(days.first(), tipOfTheDay(spanish, start.plusDays(spanish.size.toLong()))!!.id)
        // The same day picks the same tip in both languages.
        assertEquals(tipOfTheDay(spanish, start)!!.id, tipOfTheDay(english, start)!!.id)
    }
}
