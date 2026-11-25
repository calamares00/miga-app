package org.calamares.miga.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.model.RecipeFilter
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The filter sheet: allergens, coeliacs and the long lists folded away. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val categories = listOf(
        "Aperitivos", "Arroces", "Bebidas", "Carnes", "Cremas", "Desayunos", "Ensaladas", "Entrantes",
        "Guarniciones", "Helados", "Huevos", "Legumbres", "Masas", "Panes", "Pastas", "Postres"
    )

    @Test
    fun `allergens and coeliacs go into the filter`() {
        var applied by mutableStateOf(RecipeFilter())
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface {
                    Column {
                        FilterSheetContent(
                            filter = RecipeFilter(),
                            availableCategories = categories,
                            availableTags = listOf("rápida", "vegetariana"),
                            availableUtensils = listOf("horno", "olla exprés"),
                            availableIngredients = listOf("ajo", "huevos", "panceta"),
                            availableOrigins = listOf("ES", "IT"),
                            onApply = { applied = it },
                            onClear = {},
                            resultCount = 23,
                            onShowResults = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Para celíacos").performClick()
        compose.onNodeWithText("Huevo").performClick()
        compose.onNodeWithText("Leche").performClick()
        compose.onNodeWithText(org.calamares.miga.data.model.Difficulty.MEDIUM.label).performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/filters_top.png")
        assertEquals(setOf(Allergen.EGGS, Allergen.MILK), applied.withoutAllergens)
        assertEquals(true, applied.coeliacFriendly)
        assertEquals(false, applied.allowMayContain)

        compose.onNodeWithText("Incluir las que dependen del producto").performClick()
        compose.waitForIdle()
        assertEquals(true, applied.allowMayContain)

        // Long lists: categories show a few and "see all"; ingredients fold until tapped.
        compose.onNodeWithText("Postres").assertDoesNotExist()
        compose.onNodeWithText("Ver las 16").performScrollTo().performClick()
        compose.onNodeWithText("Postres").assertExists()
        compose.onNodeWithText("panceta").assertDoesNotExist()
        compose.onNodeWithText("Ingredientes").performScrollTo().performClick()
        compose.onNodeWithText("panceta").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(setOf("panceta"), applied.ingredients)
        compose.onNodeWithText("Ya cocinadas").performScrollTo()
        captureScreenRoboImage("src/test/snapshots/filters_bottom.png")
        compose.onNodeWithText("Ver 23 recetas").assertExists()
    }
}
