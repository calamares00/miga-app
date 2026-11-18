package org.calamares.miga.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.health.AllergenDisplay
import org.calamares.miga.data.health.HealthPrefs
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings > Health, allergens block: display mode, own allergens and the switches that use them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HealthSettingsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `choosing allergens and how they are shown`() {
        var health by mutableStateOf(HealthPrefs())
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HealthAllergenSettings(health) { change -> health = change(health) }
                    }
                }
            }
        }
        // The switches that use your allergens only appear once you have some.
        compose.onNodeWithText("Avisar en las listas de recetas").assertDoesNotExist()
        compose.onNodeWithText("Solo los míos").performClick()
        compose.onNodeWithText("Huevo").performClick()
        compose.onNodeWithText("Leche").performClick()
        compose.waitForIdle()
        assertEquals(AllergenDisplay.MINE, health.allergenDisplay)
        assertEquals(setOf(Allergen.EGGS, Allergen.MILK), health.myAllergens)
        compose.onNodeWithText("Ocultarlas al buscar").performClick()
        compose.waitForIdle()
        assertEquals(true, health.hideMineInSearch)
        captureScreenRoboImage("src/test/snapshots/health_settings.png")
    }
}
