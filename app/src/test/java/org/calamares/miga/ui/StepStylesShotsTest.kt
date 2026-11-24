package org.calamares.miga.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.ui.components.FormattedText
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** How steps look: times and heat in colour, ingredients in italics, bold only for a warning (MIGA_SHOTS_DIR). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepStylesShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val ingredients = listOf(
        Ingredient("Contramuslos de pollo troceados", 1.2, "kg"),
        Ingredient("Cabezas de ajo", 2.0, null),
        Ingredient("Vino blanco", 150.0, "ml"),
        Ingredient("Perejil seco", null, null),
        Ingredient("Aceite de oliva virgen extra", null, null),
        Ingredient("Pimienta negra molida", null, null),
        Ingredient("Sal", null, null)
    )

    private val steps = listOf(
        "Verter abundante aceite de oliva virgen extra en una sartén y calentar a fuego medio (nivel 6 de 9).",
        "Subir el fuego al máximo (nivel 9 de 9) y añadir los contramuslos de pollo troceados. Dejarlos dorar **sin moverlos** para evitar que suelten agua.",
        "Sazonar el pollo con pimienta negra molida y sal.",
        "Reincorporar los ajos reservados y saltear todo junto durante 1 o 2 minutos.",
        // As the assistant polished it before: its marks over times and heat no longer mix colours.
        "Añadir el *perejil seco*, tapar y cocinar a **fuego medio**-**fuerte** durante unos **8 a 10 minutos** para que el *vino blanco* reduzca."
    )

    @Test
    fun steps() {
        assumeTrue(System.getenv("MIGA_SHOTS_DIR") != null)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Preparación", style = MaterialTheme.typography.headlineSmall)
                        steps.forEachIndexed { index, step ->
                            FormattedText("${index + 1}. $step", style = MaterialTheme.typography.bodyLarge, highlightTimes = true, ingredients = ingredients)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("${System.getenv("MIGA_SHOTS_DIR")}/steps.png")
    }
}
