package org.calamares.miga.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.list.RecipeListScreen
import org.calamares.miga.ui.list.RecipeListViewModel
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.search.GlobalSearchScreen
import org.calamares.miga.ui.search.GlobalSearchViewModel
import org.calamares.miga.ui.settings.SettingsHomeScreen
import org.calamares.miga.ui.settings.SettingsPanes
import org.calamares.miga.ui.settings.SettingsSectionScreen
import org.calamares.miga.ui.settings.SettingsViewModel
import org.calamares.miga.ui.shoppinglist.ShoppingListScreen
import org.calamares.miga.ui.shoppinglist.ShoppingListViewModel
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shopping and Settings on large screens, the search start, "What can I cook?" and undoing a deletion. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeScreenAndSearchTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() = runBlocking {
        app.settingsRepository.clearRecentSearches()
        app.settingsRepository.setPantryItems(emptyList())
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun waitForText(text: String) = compose.waitUntil(10_000) { count(text) > 0 }

    private fun showShopping() {
        val viewModel = ShoppingListViewModel(app.repository, app.settingsRepository)
        compose.setContent { MigaTheme(darkTheme = false) { ShoppingListScreen(viewModel) } }
        waitForText(L10n.str(R.string.list_empty))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on a tablet the shopping lists stay beside the list`() {
        showShopping()
        waitForText(L10n.str(R.string.my_lists))
        captureScreenRoboImage("src/test/snapshots/shopping_two_panes.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun `on a phone the shopping lists are in a sheet`() {
        showShopping()
        assertEquals(0, count(L10n.str(R.string.my_lists)))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on a tablet a settings section opens beside the list`() {
        val viewModel = SettingsViewModel(app.repository, app.settingsRepository)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                SettingsPanes(
                    home = { selected, onOpenInPane ->
                        SettingsHomeScreen(
                            viewModel = viewModel,
                            hasChangelog = false,
                            onOpenSection = onOpenInPane ?: {},
                            onOpenStats = {},
                            onOpenChangelog = {},
                            onHelp = {},
                            onAbout = {},
                            selectedSection = selected
                        )
                    },
                    section = { section ->
                        SettingsSectionScreen(
                            viewModel = viewModel,
                            section = section,
                            onBack = null,
                            onManageContent = {},
                            onCleanUpContent = {},
                            onOpenPacksCatalog = {},
                            onOpenSyncConnections = {}
                        )
                    }
                )
            }
        }
        waitForText(L10n.str(R.string.appearance))
        compose.onNodeWithText(L10n.str(R.string.appearance)).performClick()
        compose.waitForIdle()
        // Its title shows twice: in the list and over the section.
        compose.waitUntil(10_000) { count(L10n.str(R.string.appearance)) >= 2 }
        captureScreenRoboImage("src/test/snapshots/settings_two_panes.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun `the search starts with what can I cook and finds recipes by what you have`() {
        runBlocking {
            val bookId = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "pantry", name = "Casa", coverPhotoUri = null, createdAt = 0L))
            fun ingredients(vararg names: String) = listOf(IngredientGroup(null, names.map { Ingredient(it, null, null) }))
            app.repository.saveRecipe(emptyRecipeDraft(bookId).copy(name = "Tortilla de patatas", ingredientGroups = ingredients("Huevos", "Patatas", "Sal")))
            app.repository.saveRecipe(emptyRecipeDraft(bookId).copy(name = "Bizcocho", ingredientGroups = ingredients("Huevos", "Harina", "Azúcar")))
            app.settingsRepository.addRecentSearch("croquetas")
        }
        val viewModel = GlobalSearchViewModel(app.repository, app.settingsRepository)
        compose.setContent { MigaTheme(darkTheme = false) { GlobalSearchScreen(viewModel = viewModel, onRecipeClick = {}) } }
        waitForText("croquetas")
        captureScreenRoboImage("src/test/snapshots/search_start.png")

        compose.onNodeWithText(L10n.str(R.string.pantry_card_body)).performClick()
        waitForText(L10n.str(R.string.pantry_empty_body))
        compose.onNodeWithText(L10n.str(R.string.pantry_what_you_have)).performTextInput("huevo, patata")
        compose.onNodeWithContentDescription(L10n.str(R.string.add)).performClick()
        waitForText("Tortilla de patatas")
        waitForText(L10n.str(R.string.pantry_have_everything))
        waitForText(L10n.str(R.string.pantry_missing_x, "Harina, Azúcar"))
        assertEquals(listOf("huevo", "patata"), runBlocking { app.settingsRepository.observePantryItems().first() })
        captureScreenRoboImage("src/test/snapshots/search_pantry.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun `a deleted recipe disappears at once and comes back with undo`() {
        val bookId = runBlocking {
            val id = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "undo", name = "Postres", coverPhotoUri = null, createdAt = 0L))
            app.repository.saveRecipe(emptyRecipeDraft(id).copy(name = "Flan"))
            id
        }
        val viewModel = RecipeListViewModel(app.repository, bookId, app.settingsRepository)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                RecipeListScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onRecipeClick = {},
                    onEditRecipeClick = {},
                    onAddRecipeClick = {},
                    onAddRecipesFromPhotos = {},
                    onSearchDishClick = {},
                    onAddRecipeFromUrl = {}
                )
            }
        }
        waitForText("Flan")
        val flanId = runBlocking { app.repository.getRecipesForBookOnce(bookId).single().id }
        viewModel.startSelection(flanId)
        viewModel.deleteSelected()
        waitForText(L10n.str(R.string.recipe_deleted))
        compose.waitUntil(10_000) { count("Flan") == 0 }
        waitForText(L10n.str(R.string.empty_book_title))
        compose.onNodeWithText(L10n.str(R.string.undo)).performClick()
        waitForText("Flan")
        assertTrue(app.repository.pendingDeletions.recipeIds.value.isEmpty())
    }
}
