package org.calamares.miga.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.carbs.FoodGroups
import org.calamares.miga.data.health.Meal
import org.calamares.miga.data.health.PortionPlan
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.settings.PortionPlanEditor
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The portion plan: editing it, and a recipe fitted to a meal. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h1200dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val lentils = Recipe(
        id = 1, uid = "u", recipeBookId = 1, recipeBookName = "Libro", name = "Lentejas", categoryId = null,
        categoryName = null, difficulty = Difficulty.EASY, prepTimeMinutes = null, cookTimeMinutes = null,
        servings = 4, notes = "", source = "", isFavorite = false, timesCooked = 0, createdAt = 0, updatedAt = 0,
        photos = emptyList(),
        ingredientGroups = listOf(
            IngredientGroup(
                null,
                listOf(
                    Ingredient("lentejas pardinas", 300.0, "g"),
                    Ingredient("patatas", 2.0, null),
                    Ingredient("zanahoria", 1.0, null),
                    Ingredient("cebolla", 1.0, null),
                    Ingredient("chorizo", 100.0, "g"),
                    Ingredient("aceite de oliva", 3.0, "cucharada")
                )
            )
        ),
        stepGroups = emptyList(), tags = emptyList(), utensils = emptyList()
    )

    @Test
    fun `lentils fitted to lunch`() {
        val perServing = FoodGroups.ofRecipe(lentils)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) { PlanLine(perServing) {} }
                        PlanSheetContent(perServing, PortionPlan.EXAMPLE_1500, 4, initialMeal = Meal.LUNCH)
                    }
                }
            }
        }
        compose.onNodeWithText("Para completar tu comida").assertExists()
        captureScreenRoboImage("src/test/snapshots/plan_fit_lunch.png")
        compose.onNodeWithText("Cena").performClick()
        compose.onNodeWithText("Desayuno").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `plan editor`() {
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface { PortionPlanEditor(PortionPlan.EXAMPLE_1500) {} }
            }
        }
        compose.onNodeWithText("Mi plan de raciones").assertExists()
        captureScreenRoboImage("src/test/snapshots/plan_editor.png")
    }

    @Test
    fun `cod pil pil fitted to lunch`() {
        val pilPil = lentils.copy(
            name = "Bacalao al pil pil",
            ingredientGroups = listOf(
                IngredientGroup(
                    null,
                    listOf(
                        Ingredient("lomos de bacalao desalado con piel", 4.0, null),
                        Ingredient("dientes de ajo", 6.0, null),
                        Ingredient("guindilla", 1.0, null),
                        Ingredient("aceite de oliva virgen extra", 300.0, "ml")
                    )
                )
            )
        )
        val perServing = FoodGroups.ofRecipe(pilPil)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface { PlanSheetContent(perServing, PortionPlan.EXAMPLE_1500, 4, initialMeal = Meal.LUNCH) }
            }
        }
        compose.onNodeWithText("Sírvete 1 ración de la receta").assertExists()
        compose.onNodeWithText("2 vasos medidores", substring = true).assertExists()
        captureScreenRoboImage("src/test/snapshots/plan_fit_pil_pil.png")
    }

    @Test
    fun `burgers at bedtime do not fit`() {
        val burgers = lentils.copy(
            ingredientGroups = listOf(
                IngredientGroup(
                    null,
                    listOf(
                        Ingredient("carne picada mixta (ternera y cerdo)", 400.0, "g"),
                        Ingredient("cebolla", 100.0, "g"),
                        Ingredient("pan de molde", 2.0, "rebanada"),
                        Ingredient("leche", 100.0, "g"),
                        Ingredient("huevo", 1.0, null)
                    )
                )
            )
        )
        val perServing = FoodGroups.ofRecipe(burgers)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface { PlanSheetContent(perServing, PortionPlan.EXAMPLE_1500, 4, initialMeal = Meal.BEDTIME) }
            }
        }
        compose.onNodeWithText("No encaja en «Antes de dormir»").assertExists()
        compose.onNodeWithText("Encaja en: comida, cena.").assertExists()
        captureScreenRoboImage("src/test/snapshots/plan_fit_not_suitable.png")
    }
}
