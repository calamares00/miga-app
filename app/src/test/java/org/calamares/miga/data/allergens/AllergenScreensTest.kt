package org.calamares.miga.data.allergens

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.detail.RecipeDetailPager
import org.calamares.miga.ui.detail.RecipeDetailViewModel
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The allergen chip, line and sheet of a recipe, and the editor's certified marks. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllergenScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    private fun show(name: String, vararg ingredients: String) {
        val id = runBlocking {
            val bookId = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "b$name", name = "Casa", coverPhotoUri = null, createdAt = 0L))
            app.repository.saveRecipe(
                emptyRecipeDraft(bookId).copy(
                    name = name,
                    servings = 4,
                    ingredientGroups = listOf(IngredientGroup(null, ingredients.map { Ingredient(it, null, null) }))
                )
            )
        }
        compose.setContent {
            MigaTheme(darkTheme = false) {
                RecipeDetailPager(
                    recipeIds = listOf(id),
                    initialRecipeId = id,
                    viewModelFor = { remember(it) { RecipeDetailViewModel(app.repository, it, app.settingsRepository) } },
                    onBack = {},
                    onEdit = {}
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a recipe shows its allergens in one line and the details in a sheet`() {
        show("Lentejas con chorizo", "300 g de lentejas pardinas", "1 chorizo", "1 pastilla de caldo de verduras", "1 rama de apio", "1 vaso de vino blanco", "2 zanahorias", "1 cebolla")
        compose.onNodeWithText(L10n.str(R.string.allergens_title)).performScrollTo()
        assertTrue(shown(L10n.str(R.string.coeliac_suitable_if)))
        captureScreenRoboImage("src/test/snapshots/allergens_recipe.png")

        compose.onNodeWithText(L10n.str(R.string.allergens_title)).performClick()
        compose.waitUntil(5_000) { shown(L10n.str(R.string.allergens_sheet_title)) }
        assertTrue(shown(L10n.str(R.string.allergen_may_contain_x, Allergen.GLUTEN.label)))
        assertTrue(shown(L10n.str(R.string.allergens_disclaimer)))
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/allergens_sheet.png")
    }

    @Test
    fun `a recipe without gluten ingredients says so, never just gluten-free`() {
        show("Tortilla de patatas", "6 huevos", "700 g de patatas", "1 cebolla", "aceite de oliva", "sal")
        assertTrue(shown(L10n.str(R.string.gluten_free_ingredients)))
        assertFalse(shown(L10n.str(R.string.allergens_none)))
    }

    @Test
    fun `certified marks are added to the name and removed again`() {
        val mark = CertifiedFreeMarks.GLUTEN_FREE
        val marked = mark.toggle("chorizo")
        assertEquals("chorizo ${L10n.str(R.string.ingredient_gluten_free_phrase)}", marked)
        assertTrue(mark.isIn(marked))
        assertFalse(Allergen.GLUTEN in AllergenAnalysis.ofIngredient(marked).levels)
        assertEquals("chorizo", mark.toggle(marked))
        assertFalse(mark.isIn("chorizo singluten"))
    }
}
