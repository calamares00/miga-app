package org.calamares.miga.ui.detail

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.calamares.miga.MigaApp
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Health and nutrition carry a single AI notice between them, not one each. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HealthNoticeTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `health and nutrition show one notice`() {
        val recipeId = runBlocking {
            val bookId = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "book", name = "Familia", coverPhotoUri = null, createdAt = 0L))
            val id = app.repository.saveRecipe(emptyRecipeDraft(bookId).copy(name = "Chocolate cake"))
            app.repository.saveHealthRating(id, HealthColorLevel.RED, "High in sugar and fat.", "f", 1L)
            app.repository.saveNutritionInfo(id, 285, 5.2, 40.5, 11.8, "f", 1L)
            id
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
        val notice = "AI-generated estimate. It may contain errors and is not a substitute for advice from a health professional."
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(notice)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, compose.onAllNodes(hasText(notice)).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasText("Report", substring = true)).fetchSemanticsNodes().size)
        compose.onNodeWithText(notice).performScrollTo()
        captureScreenRoboImage("src/test/snapshots/health_nutrition_notice.png")
    }
}
