package org.calamares.miga.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.allergens.AllergenAnalysis
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recipe's allergen row with "only mine" and coeliacs (Settings > Health). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MyAllergenLineTest {

    @get:Rule
    val compose = createComposeRule()

    private val mine = setOf(Allergen.EGGS, Allergen.MILK)
    private val tortilla = AllergenAnalysis.ofIngredients(listOf("huevos", "patatas", "aceite de oliva", "sal").map { Ingredient(it, null, null) })
    private val lentils = AllergenAnalysis.ofIngredients(listOf("lentejas", "zanahoria", "cebolla", "pimentón dulce").map { Ingredient(it, null, null) })

    @Test
    fun `only mine and coeliacs`() {
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Solo los míos (huevo, leche) + celíacos")
                        MyAllergenLine(tortilla, mine, coeliac = true) {}
                        MyAllergenLine(lentils, mine, coeliac = true) {}
                        Text("Solo los míos, sin elegir ninguno")
                        MyAllergenLine(lentils, emptySet(), coeliac = false) {}
                        Text("Alérgenos: no · celíacos")
                        MyAllergenLine(lentils, null, coeliac = true) {}
                        Text("Alérgenos: no · sin celíacos (no se pinta nada)")
                        MyAllergenLine(lentils, null, coeliac = false) {}
                    }
                }
            }
        }
        compose.onNodeWithText("Lleva huevo").assertExists()
        compose.onNodeWithText("Sin tus alérgenos").assertExists()
        compose.onNodeWithText("Elige tus alérgenos en Ajustes › Salud").assertExists()
        captureScreenRoboImage("src/test/snapshots/my_allergen_line.png")
    }
}
