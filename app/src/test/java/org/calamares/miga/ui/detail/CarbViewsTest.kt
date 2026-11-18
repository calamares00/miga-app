package org.calamares.miga.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.carbs.CarbAnalysis
import org.calamares.miga.data.carbs.RecipeCarbs
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The carbohydrate line of a recipe and its sheet, in Spanish portions of 10 g. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarbViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val lentils = RecipeCarbs(
        listOf(
            Ingredient("lentejas pardinas", 300.0, "g"),
            Ingredient("patatas", 2.0, null),
            Ingredient("zanahorias", 2.0, null),
            Ingredient("cebolla", 1.0, null),
            Ingredient("ajo", 2.0, "diente"),
            Ingredient("chorizo", 1.0, null),
            Ingredient("pimentón dulce", 1.0, "cucharadita"),
            Ingredient("aceite de oliva", 3.0, "cucharada"),
            Ingredient("pan", null, null, optional = true),
            Ingredient("harina para espesar", null, null)
        ).map { CarbAnalysis.ofIngredient(it) },
        servings = 4
    )

    @Test
    fun `line and sheet`() {
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CarbLine(lentils, 10) {}
                        CarbSheetContent(lentils, 10)
                    }
                }
            }
        }
        compose.onNodeWithText("Por ingrediente", substring = true).assertExists()
        compose.onNodeWithText("falta 1 ingrediente", substring = true).assertExists()
        captureScreenRoboImage("src/test/snapshots/carbs_sheet.png")
    }

    @Test
    fun `portions round to halves and grams only at 0`() {
        assertEquals("3½ raciones", carbAmount(34.0, 10))
        assertEquals("1 ración", carbAmount(9.0, 10))
        assertEquals("½ ración", carbAmount(5.5, 10))
        assertEquals("menos de ½ ración", carbAmount(2.0, 10))
        assertEquals("2 raciones", carbAmount(31.0, 15))
        assertEquals("35 g", carbAmount(34.6, 0))
    }
}
