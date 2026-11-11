package org.calamares.miga.ui.list

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real application, without the periodic sync: there is no WorkManager on the JVM. */
class TestMigaApp : MigaApp() {
    override fun schedulePeriodicSync() = Unit
}

/**
 * The key flows of a book's screen, driven through the interface as a user would: selecting
 * recipes, the share options offered for them and importing a file with several recipes. Each one
 * also leaves a reference screenshot (see app/build.gradle.kts).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipeListFlowsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private lateinit var viewModel: RecipeListViewModel

    @Before
    fun setUp() = runBlocking {
        val bookId = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "book", name = "Familia", coverPhotoUri = null, createdAt = 0L))
        listOf("Paella", "Tortilla", "Gazpacho").forEach { app.repository.saveRecipe(emptyRecipeDraft(bookId).copy(name = it)) }
        viewModel = RecipeListViewModel(app.repository, bookId, app.settingsRepository)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                RecipeListScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onRecipeClick = {},
                    onEditRecipeClick = {},
                    onAddRecipeClick = {},
                    onAddRecipeFromPhoto = {},
                    onAddRecipesBulk = {},
                    onSearchDishClick = {},
                    onAddRecipeFromUrl = {}
                )
            }
        }
        waitForText("Paella")
    }

    /** The lists come from the database on another thread: wait for them instead of assuming. */
    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun gone(text: String) =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()

    private fun snapshot(name: String) = captureScreenRoboImage("src/test/snapshots/$name.png")

    @Test
    fun `a long press starts a selection and a tap adds to it`() {
        compose.onNodeWithText("Paella").performTouchInput { longClick() }
        waitForText(selectedRecipesText(1))

        compose.onNodeWithText("Tortilla").performClick()
        waitForText(selectedRecipesText(2))
        snapshot("book_selection")

        compose.onNodeWithContentDescription(L10n.str(R.string.cancel_selection)).performClick()
        waitForText("Familia")
        assert(gone(selectedRecipesText(2)))
    }

    @Test
    fun `one recipe can be sent to someone, several only as a document or a copy`() {
        compose.onNodeWithText("Paella").performTouchInput { longClick() }
        waitForText(selectedRecipesText(1))
        compose.onNodeWithContentDescription(L10n.str(R.string.share)).performClick()
        waitForText(L10n.str(R.string.share_x, "Paella"))
        compose.onNodeWithText(L10n.str(R.string.share_action_send)).assertIsDisplayed()
        compose.onNodeWithText(L10n.str(R.string.export_format_pdf)).assertIsDisplayed()
        snapshot("share_one_recipe")
    }

    @Test
    fun `several recipes are shared as a document or a copy only`() {
        compose.onNodeWithText("Paella").performTouchInput { longClick() }
        waitForText(selectedRecipesText(1))
        compose.onNodeWithText("Tortilla").performClick()
        waitForText(selectedRecipesText(2))
        compose.onNodeWithContentDescription(L10n.str(R.string.share)).performClick()
        waitForText(L10n.str(R.string.share_x, selectedRecipesText(2)))
        compose.onNodeWithText(L10n.str(R.string.export_format_pdf)).assertIsDisplayed()
        compose.onNodeWithText(L10n.str(R.string.share_action_copy)).assertIsDisplayed()
        assert(gone(L10n.str(R.string.share_action_send)))
    }

    @Test
    fun `a file with several recipes asks where they go and adds them to the book`() {
        val file = runBlocking {
            // Written by "another phone": its own database, so the recipes are new here.
            val sender = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
            val repository = RecipeRepository(sender)
            val bookId = sender.recipeBookDao().insert(RecipeBookEntity(uid = "theirs", name = "Postres", coverPhotoUri = null, createdAt = 0L))
            listOf("Flan", "Natillas").forEach { repository.saveRecipe(emptyRecipeDraft(bookId).copy(name = it)) }
            val book = repository.books.getRecipeBookOnce(bookId)!!
            RecipeExporter.writeMigaFile(
                app, book.name, listOf(book), repository.getRecipesForBookOnce(bookId),
                singleRecipe = false, onPhoto = { _, _ -> }, forSharing = true, sharedBy = "Ana"
            ).also { sender.close() }
        }

        viewModel.importRecipeFile(app, Uri.fromFile(file)) {}
        waitForText(L10n.str(R.string.import_n_recipes_title, 2))
        snapshot("import_recipes_dialog")

        compose.onNodeWithText(L10n.str(R.string.import_action)).performClick()
        waitForText("Flan")
        waitForText("Natillas")
        compose.onNodeWithText("Paella").assertIsDisplayed()
    }
}
