package org.calamares.miga.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The ⚠ next to ingredients follows Settings > Health and explains itself when tapped. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IngredientWarningIconTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `only gluten is watched and the warning explains itself`() {
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface {
                    Column {
                        listOf(Ingredient("caldo", 200.0, "g"), Ingredient("vinagre", 10.0, "g"), Ingredient("pan rallado", 20.0, "g")).forEach {
                            IngredientLine(it, 1.0, false, {}, null, watchedAllergens = setOf(Allergen.GLUTEN))
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Puede llevar gluten según el producto: mira la etiqueta").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Puede llevar gluten según el producto: mira la etiqueta").assertExists()
        compose.onNodeWithContentDescription("Lleva gluten").assertExists()
        captureScreenRoboImage("src/test/snapshots/ingredient_warning.png")
    }
}
