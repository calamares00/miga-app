package org.calamares.miga.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderModelsTest {

    @Test
    fun `only OpenAI chat models usable with chat completions are listed`() {
        val ids = listOf(
            "gpt-5-mini", "gpt-4o", "o4-mini", "chatgpt-4o-latest",
            "gpt-4o-audio-preview", "gpt-4o-realtime-preview", "gpt-4o-mini-tts", "gpt-image-1",
            "text-embedding-3-small", "whisper-1", "dall-e-3", "gpt-5-codex", "o1-pro", "gpt-3.5-turbo", "omni-moderation-latest"
        )
        assertEquals(listOf("gpt-5-mini", "gpt-4o", "o4-mini", "chatgpt-4o-latest"), ids.filter { isOpenAiChatModel(it) })
    }

    @Test
    fun `only Gemini text models are listed`() {
        val ids = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash-preview-tts", "gemini-2.5-flash-image", "gemini-live-2.5", "gemma-3-27b-it")
        assertEquals(listOf("gemini-3.6-flash", "gemini-3.5-flash-lite"), ids.filter { isGeminiChatModel(it) })
    }

    @Test
    fun `small reasoning models do not read images`() {
        assertEquals(true, openAiModelReadsImages("gpt-5-mini"))
        assertEquals(false, openAiModelReadsImages("o3-mini"))
    }
}
