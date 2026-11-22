package org.calamares.miga.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.calamares.miga.ui.welcome.WelcomeScreen
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every page of the welcome tour, for review and the website. Off in normal runs; run
 * recordRoborazziDebug with MIGA_SHOTS_DIR set.
 */
abstract class WelcomeShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val out: String? = System.getenv("MIGA_SHOTS_DIR")

    @Test
    fun tour() {
        assumeTrue(out != null)
        compose.setContent { MigaTheme(darkTheme = false) { Surface(Modifier.fillMaxSize()) { WelcomeScreen("packs", "backup") {} } } }
        val pages = 9
        for (page in 1..pages) {
            compose.waitForIdle()
            Thread.sleep(600)
            captureScreenRoboImage("$out/tour_$page.png")
            if (page < pages) {
                val next = if (page == pages - 1) R.string.tour_continue else R.string.next_2
                compose.onAllNodesWithText(L10n.str(next)).onFirst().performClick()
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeShotsEsTest : WelcomeShotsTest()

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "en-rGB-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeShotsEnTest : WelcomeShotsTest()
