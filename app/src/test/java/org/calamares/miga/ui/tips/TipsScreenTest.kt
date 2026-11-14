package org.calamares.miga.ui.tips

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.calamares.miga.MigaApp
import org.calamares.miga.data.tips.CookingTips
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.robolectric.annotation.GraphicsMode
import org.junit.Rule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The tips screen: opening on a tip, saving it, swiping between filters and searching. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TipsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val tips = CookingTips.all(app, "en")
    private val viewModel = TipsViewModel(app.settingsRepository, tips)

    @Before
    fun clearSaved() = runBlocking {
        app.settingsRepository.observeSavedTips().first().forEach { app.settingsRepository.setTipSaved(it, false) }
    }

    private fun show(initialTipId: String? = null) {
        compose.setContent {
            MigaTheme(darkTheme = false) { TipsScreen(viewModel = viewModel, initialTipId = initialTipId, onBack = {}) }
        }
        compose.waitForIdle()
    }

    private fun snapshot(name: String) = captureScreenRoboImage("src/test/snapshots/$name.png")

    @Test
    fun `opening on a tip shows it whole, and its star saves it`() {
        val tip = tips.first { it.id == "aubergine-salt" }
        show(tip.id)
        compose.onNodeWithText(tip.body).assertExists()
        snapshot("tips_open_tip")
        compose.onNodeWithContentDescription("Save tip").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Saved (1)")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { app.settingsRepository.observeSavedTips().first() } == setOf(tip.id))
    }

    @Test
    fun `a swipe moves from every tip to the saved ones`() {
        show()
        snapshot("tips_all")
        compose.onAllNodes(isRoot())[0].performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("No saved tips").assertExists()
    }

    @Test
    fun `search finds tips in every category`() {
        show()
        compose.onNodeWithText("Search tips").performTextInput("garlic")
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText("Peel garlic in no time")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Don't burn the garlic")).fetchSemanticsNodes().isNotEmpty())
    }
}
