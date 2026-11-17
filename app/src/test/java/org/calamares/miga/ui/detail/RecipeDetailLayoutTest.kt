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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A long recipe in the compact layout: facts in one strip, amounts in a column, highlighted steps. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipeDetailLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `a long recipe fits its facts, allergens and most ingredients on the first screen`() {
        val id = runBlocking {
            val bookId = app.database.recipeBookDao().insert(RecipeBookEntity(uid = "layout", name = "Casa", coverPhotoUri = null, createdAt = 0L))
            fun i(name: String, quantity: Double?, unit: String?) = Ingredient(name, quantity, unit)
            app.repository.saveRecipe(
                emptyRecipeDraft(bookId).copy(
                    name = "Aguja de ternera a la jardinera",
                    categoryName = "Carnes",
                    servings = 4,
                    prepTimeMinutes = 20,
                    cookTimeMinutes = 50,
                    ingredientGroups = listOf(
                        IngredientGroup(
                            null,
                            listOf(
                                i("aguja de ternera", 600.0, "g"), i("agua", 800.0, "g"), i("aceite de oliva", 70.0, "g"),
                                i("cebolla picada", 150.0, "g"), i("zanahoria en cuadros", 1.0, null), i("nabos", 100.0, "g"),
                                i("ajo", 2.0, "diente"), i("laurel", 2.0, "hoja"), i("vino blanco", 150.0, "g"),
                                i("pastilla de caldo de carne", 1.0, null), i("guisantes", 100.0, "g"), i("judías verdes", 100.0, "g"),
                                i("patatas", 200.0, "g"), i("zanahorias", 2.0, null), i("espárragos verdes", 8.0, null),
                                i("alcachofas escaldadas y troceadas", 1.0, "lata")
                            )
                        )
                    ),
                    stepGroups = listOf(
                        StepGroup(
                            null,
                            listOf(
                                "Corte en trozos pequeños la carne y rehóguela en una sartén con un poco de aceite. Reserve.",
                                "Añada al vaso la cebolla, las zanahorias, los nabos y los ajos, y programe 6 seg, velocidad 3½.",
                                "Añada el aceite y sofría 5 min, 100 °C, velocidad 1."
                            )
                        )
                    )
                )
            )
        }
        compose.setContent {
            MigaTheme(darkTheme = true, colorTheme = ColorTheme.BLUE) {
                RecipeDetailPager(
                    recipeIds = listOf(id),
                    initialRecipeId = id,
                    viewModelFor = { remember(it) { RecipeDetailViewModel(app.repository, it, app.settingsRepository) } },
                    onBack = {},
                    onEdit = {}
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("aguja de ternera")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(hasText("1 h 10 min")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("600 g")).fetchSemanticsNodes().isNotEmpty())
        captureScreenRoboImage("src/test/snapshots/recipe_compact_top.png")
        compose.onNodeWithText("Añada el aceite", substring = true).performScrollTo()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/recipe_compact_steps.png")
    }
}
