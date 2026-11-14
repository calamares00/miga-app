package org.calamares.miga.ui.tips

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
import org.calamares.miga.MigaApp
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.detail.RecipeDetailPager
import org.calamares.miga.ui.detail.RecipeDetailViewModel
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A recipe shows the tips that help with it, and they open in a sheet. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipeTipsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `a recipe with aubergine offers its tips`() {
        val recipeId = runBlocking {
            val bookId = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "book", name = "Familia", coverPhotoUri = null, createdAt = 0L))
            app.repository.saveRecipe(
                emptyRecipeDraft(bookId).copy(
                    name = "Stuffed aubergines",
                    ingredientGroups = listOf(IngredientGroup(null, listOf(Ingredient("aubergines", 2.0, null), Ingredient("minced beef", 300.0, "g")))),
                    stepGroups = listOf(StepGroup(null, listOf("Halve the aubergines and bake them.")))
                )
            )
        }
        compose.setContent {
            MigaTheme(darkTheme = false) {
                RecipeDetailPager(
                    recipeIds = listOf(recipeId),
                    initialRecipeId = recipeId,
                    viewModelFor = { id -> remember(id) { RecipeDetailViewModel(app.repository, id, app.settingsRepository) } },
                    onBack = {},
                    onEdit = {}
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("tips for this recipe", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("tips for this recipe", substring = true).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Aubergine that doesn't soak up oil")).fetchSemanticsNodes().isNotEmpty() }
        captureScreenRoboImage("src/test/snapshots/recipe_tips_sheet.png")
    }
}
