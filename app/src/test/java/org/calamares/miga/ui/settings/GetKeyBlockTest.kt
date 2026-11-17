package org.calamares.miga.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Get a key": after going to the provider's page and coming back, a copied key is offered. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class GetKeyBlockTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val groqKey = "gsk_" + "Ab12".repeat(13)

    private val clipboard get() = compose.activity.getSystemService(ClipboardManager::class.java)

    private fun copy(text: String) = clipboard.setPrimaryClip(ClipData.newPlainText("key", text))

    private fun goAndComeBack() {
        compose.onNodeWithText(L10n.str(R.string.ai_get_key)).performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a copied key of the provider is offered, used and removed from the clipboard`() {
        val used = mutableListOf<String>()
        compose.setContent { MigaTheme(darkTheme = false) { GetKeyBlock(AiProvider.GROQ) { used += it } } }
        copy(groqKey)
        goAndComeBack()
        compose.waitUntil(5_000) { shown(L10n.str(R.string.ai_use_key)) }
        compose.onNodeWithText(L10n.str(R.string.ai_use_key)).performClick()
        assertEquals(listOf(groqKey), used)
        assertTrue(clipboard.primaryClip?.getItemAt(0)?.text?.toString() != groqKey)
    }

    @Test
    fun `other copied text is not offered`() {
        compose.setContent { MigaTheme(darkTheme = false) { GetKeyBlock(AiProvider.GROQ) {} } }
        copy("sk-or-v1-" + "0123456789abcdef".repeat(4))
        goAndComeBack()
        compose.waitUntil(5_000) { shown(L10n.str(R.string.ai_key_not_in_clipboard_x, "Groq")) }
        assertTrue(!shown(L10n.str(R.string.ai_use_key)))
    }
}
