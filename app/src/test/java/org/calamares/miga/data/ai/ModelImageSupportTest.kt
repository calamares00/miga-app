package org.calamares.miga.data.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which models the pickers tag as reading images. */
class ModelImageSupportTest {

    @Test
    fun `every gemini and claude model reads images`() {
        assertTrue(ProviderModels.readsImages(AiProvider.GEMINI, "gemini-3.6-flash"))
        assertTrue(ProviderModels.readsImages(AiProvider.ANTHROPIC, "claude-haiku-4-5"))
    }

    @Test
    fun `openai, deepseek and the generic providers follow their rules`() {
        assertTrue(ProviderModels.readsImages(AiProvider.OPENAI, "gpt-5-mini"))
        assertFalse(ProviderModels.readsImages(AiProvider.OPENAI, "o3-mini"))
        assertTrue(ProviderModels.readsImages(AiProvider.DEEPSEEK, "deepseek-flash"))
        assertFalse(ProviderModels.readsImages(AiProvider.DEEPSEEK, "deepseek-v4-pro"))
        assertTrue(ProviderModels.readsImages(AiProvider.GROQ, "qwen/qwen3.8-27b"))
        assertFalse(ProviderModels.readsImages(AiProvider.CUSTOM, "qwen3:8b"))
    }

    @Test
    fun `an openrouter model missing from the catalogue is not tagged`() {
        assertFalse(ProviderModels.readsImages(AiProvider.OPENROUTER, "vendor/unknown-model"))
    }
}
