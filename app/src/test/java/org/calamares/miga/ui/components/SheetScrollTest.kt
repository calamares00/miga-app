package org.calamares.miga.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A sheet with a long list stays put when the list hits its end, and still closes by pulling down. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
class SheetScrollTest {

    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `flinging past the end of the list does not move the sheet`() {
        var open by mutableStateOf(true)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                if (open) {
                    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        Column(modifier = Modifier.keepScrollInSheet()) {
                            Column(
                                modifier = Modifier.testTag("list").heightIn(max = sheetContentHeight(0.9f)).verticalScroll(remember { androidx.compose.foundation.ScrollState(0) })
                            ) {
                                Text("Top", modifier = Modifier.testTag("top"))
                                repeat(40) { Text("Tip $it", modifier = Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        repeat(6) { compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 80) } }
        compose.waitForIdle()
        val sheetTop = compose.onNodeWithTag("list").getBoundsInRoot().top
        repeat(3) {
            compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 60) }
            compose.waitForIdle()
            assertEquals(sheetTop, compose.onNodeWithTag("list").getBoundsInRoot().top)
        }
        // Back to the top, pulling down still closes it.
        for (attempt in 1..12) {
            if (!open) break
            compose.onNodeWithTag("list").performTouchInput { swipeDown(durationMillis = 120) }
            compose.waitForIdle()
        }
        assertFalse(open)
    }
}
