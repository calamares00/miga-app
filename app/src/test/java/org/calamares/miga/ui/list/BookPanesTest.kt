package org.calamares.miga.ui.list

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.detail.RecipeDetailPager
import org.calamares.miga.ui.detail.RecipeDetailViewModel
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A book on a tablet (list and recipe side by side) and on a phone (the list alone). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookPanesTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val openedAsScreen = mutableListOf<Long>()

    private fun showBook() {
        val bookId = runBlocking {
            val id = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "book", name = "Familia", coverPhotoUri = null, createdAt = 0L))
            listOf("Paella", "Tortilla").forEach { app.repository.saveRecipe(emptyRecipeDraft(id).copy(name = it, notes = "Notas de $it")) }
            id
        }
        val listViewModel = RecipeListViewModel(app.repository, bookId, app.settingsRepository)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                BookPanes(
                    list = { onRecipeClickInPane ->
                        RecipeListScreen(
                            viewModel = listViewModel,
                            onBack = {},
                            onRecipeClick = onRecipeClickInPane ?: { openedAsScreen += it },
                            onEditRecipeClick = {},
                            onAddRecipeClick = {},
                            onAddRecipeFromPhoto = {},
                            onAddRecipesBulk = {},
                            onSearchDishClick = {},
                            onAddRecipeFromUrl = {}
                        )
                    },
                    detail = { recipeId, onClose ->
                        RecipeDetailPager(
                            recipeIds = listOf(recipeId),
                            initialRecipeId = recipeId,
                            viewModelFor = { id -> remember(id) { RecipeDetailViewModel(app.repository, id, app.settingsRepository) } },
                            onBack = onClose,
                            onEdit = {}
                        )
                    }
                )
            }
        }
        waitForText("Paella")
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) { count(text) > 0 }

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on a tablet the recipe opens next to the list`() {
        showBook()
        compose.onNodeWithText(L10n.str(R.string.choose_recipe_to_see_it)).assertIsDisplayed()

        compose.onNodeWithText("Paella").performClick()
        waitForText("Notas de Paella")
        // Still there, with the recipe beside it.
        compose.onNodeWithText("Tortilla").assertIsDisplayed()
        assertTrue(openedAsScreen.isEmpty())
        captureScreenRoboImage("src/test/snapshots/book_two_panes.png")

        compose.onNodeWithText("Tortilla").performClick()
        waitForText("Notas de Tortilla")
        assertEquals(0, count("Notas de Paella"))

        // The list has its own "back" (first); the recipe's closes the pane.
        compose.onAllNodesWithContentDescription(L10n.str(R.string.back), useUnmergedTree = true)[1].performClick()
        waitForText(L10n.str(R.string.choose_recipe_to_see_it))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun `on a phone the recipe opens as its own screen`() {
        showBook()
        assertEquals(0, count(L10n.str(R.string.choose_recipe_to_see_it)))
        compose.onNodeWithText("Paella").performClick()
        compose.waitForIdle()
        assertEquals(1, openedAsScreen.size)
    }
}
