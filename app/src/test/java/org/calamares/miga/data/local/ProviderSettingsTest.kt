package org.calamares.miga.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.calamares.miga.MigaApp
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.data.ai.DEFAULT_GEMINI_MODEL
import org.calamares.miga.data.export.SettingsBackupDto
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every provider is configured through the same settings, stored under the names earlier versions used. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class ProviderSettingsTest {

    private val settings = ApplicationProvider.getApplicationContext<MigaApp>().settingsRepository

    @Before
    fun clear() = runBlocking {
        AiProvider.entries.forEach { settings.setApiKey(it, "") }
        settings.setCustomBaseUrl("")
    }

    @Test
    fun `a key is enough for a provider with a default model`() = runBlocking {
        settings.setApiKey(AiProvider.GEMINI, "key")
        assertEquals("key", settings.apiKeyFor(AiProvider.GEMINI))
        assertEquals(DEFAULT_GEMINI_MODEL, settings.modelFor(AiProvider.GEMINI))
        assertTrue(AiProvider.GEMINI in settings.observeConfiguredProviders().first())
        assertTrue(settings.supportsImages(AiProvider.GEMINI))
        // OpenRouter has no default: a key alone does not make it ready.
        settings.setApiKey(AiProvider.OPENROUTER, "key")
        assertFalse(AiProvider.OPENROUTER in settings.observeConfiguredProviders().first())
    }

    @Test
    fun `settings saved by earlier versions are read under the same names`() = runBlocking {
        settings.restoreFromBackup(
            SettingsBackupDto(
                strings = mapOf("openai_model" to "o3-mini", "deepseek_model" to "deepseek-v4-pro", "anthropic_model" to "claude-x"),
                booleans = emptyMap()
            )
        )
        assertEquals("o3-mini", settings.modelFor(AiProvider.OPENAI))
        // Nothing stored about images: guessed from the model name, as before.
        assertFalse(settings.supportsImages(AiProvider.OPENAI))
        assertFalse(settings.supportsImages(AiProvider.DEEPSEEK))
        assertTrue(settings.supportsImages(AiProvider.ANTHROPIC))

        settings.setModel(AiProvider.GEMINI, "gemini-test")
        val backup = settings.exportForBackup()
        assertEquals("gemini-test", backup.strings["gemini_model"])
        assertEquals(true, backup.booleans["gemini_model_images"])
    }

    @Test
    fun `the images switch overrides the guess`() = runBlocking {
        settings.setModel(AiProvider.MISTRAL, "mistral-small-latest")
        assertFalse(settings.supportsImages(AiProvider.MISTRAL))
        settings.setModelImages(AiProvider.MISTRAL, true)
        assertTrue(settings.supportsImages(AiProvider.MISTRAL))
        settings.setModel(AiProvider.OPENROUTER, "vendor/model:free", readsImages = false)
        assertFalse(settings.supportsImages(AiProvider.OPENROUTER))
    }
}
