package org.calamares.miga.ui.components

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "new recipe" menu: its options in order, and which need the cooking assistant. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NewRecipeSourceSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val clicked = mutableListOf<String>()

    private fun show(aiEnabled: Boolean) = compose.setContent {
        MigaTheme(darkTheme = false) {
            Surface {
                NewRecipeSourceSheet(
                    onManualClick = { clicked += "manual" },
                    onFileClick = { clicked += "file" },
                    onPhotoClick = { clicked += "photos" },
                    onSearchDishClick = { clicked += "suggest" },
                    onUrlClick = { clicked += "web" },
                    aiEnabled = aiEnabled
                )
            }
        }
    }

    @Test
    fun `every option opens its source, photos being a single entry`() {
        show(aiEnabled = true)
        captureScreenRoboImage("src/test/snapshots/new_recipe_menu.png")
        listOf("Write it yourself", "From photos", "From a web page", "From a Miga file", "Suggest a recipe").forEach {
            compose.onNodeWithText(it).performClick()
        }
        assertEquals(listOf("manual", "photos", "web", "file", "suggest"), clicked)
    }

    @Test
    fun `without the assistant only writing and files are offered`() {
        show(aiEnabled = false)
        compose.onNodeWithText("Write it yourself").assertExists()
        compose.onNodeWithText("From a Miga file").assertExists()
        compose.onNodeWithText("From photos").assertDoesNotExist()
        compose.onNodeWithText("Suggest a recipe").assertDoesNotExist()
    }
}
