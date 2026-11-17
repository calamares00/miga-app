package org.calamares.miga.ui.editor

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The editor's compact layout: tabs, ingredients as in the recipe, quick add, steps across. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipeEditorLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    private fun shown(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `editing a recipe shows tabs, ingredient rows that open, quick add and the photo credit apart`() {
        val (bookId, recipeId) = runBlocking {
            val bookId = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "editor", name = "Navidad", coverPhotoUri = null, createdAt = 0L))
            fun i(name: String, quantity: Double?, unit: String?) = Ingredient(name, quantity, unit)
            bookId to app.repository.saveRecipe(
                emptyRecipeDraft(bookId).copy(
                    name = "Cochinillo asado",
                    categoryName = "Carnes",
                    servings = 6,
                    prepTimeMinutes = 15,
                    cookTimeMinutes = 150,
                    notes = "Encárgalo abierto en canal.\n\n📷 Foto: [Tamorlan](https://commons.wikimedia.org/wiki/File:Cochinillo.jpg) · CC BY 3.0",
                    ingredientGroups = listOf(
                        IngredientGroup(
                            null,
                            listOf(
                                i("cochinillo abierto en canal (4-5 kg)", 1.0, null), i("manteca de cerdo", 50.0, "g"), i("agua", 500.0, "ml"),
                                i("ajo", 4.0, "diente"), i("hojas de laurel", 2.0, null), i("sal gruesa", null, null)
                            )
                        )
                    ),
                    stepGroups = listOf(
                        StepGroup(
                            null,
                            listOf(
                                "Precalienta el horno a 180 °C. Sala el cochinillo por la parte interior.",
                                "Colócalo en una fuente de barro con la piel hacia abajo. Añade el agua, el ajo y el laurel.",
                                "Asa 1 h 30 min, regando de vez en cuando con el jugo."
                            )
                        )
                    ),
                    tagNames = listOf("navidad", "horno")
                )
            )
        }
        val viewModel = RecipeEditorViewModel(app.repository, app.settingsRepository, recipeId, bookId)
        compose.setContent { MigaTheme(darkTheme = false, colorTheme = ColorTheme.BLUE) { RecipeEditorScreen(viewModel = viewModel, onSaved = {}, onCancel = {}) } }
        compose.waitUntil(10_000) { shown("Cochinillo asado") }
        assertTrue(shown(L10n.str(R.string.editor_tab_count_x, L10n.str(R.string.ingredients), 6)))
        captureScreenRoboImage("src/test/snapshots/editor_basics.png")

        compose.onNodeWithText(L10n.str(R.string.editor_tab_count_x, L10n.str(R.string.ingredients), 6)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ajo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(L10n.str(R.string.editor_add_ingredient_hint)).performTextInput("200 g de patatas")
        compose.onNodeWithText("200 g de patatas").performImeAction()
        compose.waitForIdle()
        val rows = viewModel.ingredientGroups.single().ingredients
        assertEquals("patatas", rows.last().name)
        assertEquals("200", rows.last().quantity)
        assertEquals("g", rows.last().unit)
        compose.onNodeWithText(L10n.str(R.string.editor_tab_count_x, L10n.str(R.string.ingredients), 7)).performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/editor_ingredients.png")

        compose.onNodeWithText(L10n.str(R.string.editor_tab_count_x, L10n.str(R.string.editor_tab_steps), 3)).performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/editor_steps.png")

        compose.onNode(hasText(L10n.str(R.string.editor_more)) and hasClickAction()).performClick()
        compose.waitForIdle()
        assertTrue(shown("📷 Foto: Tamorlan · CC BY 3.0"))
        captureScreenRoboImage("src/test/snapshots/editor_more.png")
        // Editing the notes keeps the credit.
        assertTrue(viewModel.notes.endsWith("· CC BY 3.0"))
    }
}
