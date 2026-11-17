package org.calamares.miga.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyPagesTest {

    /** Made-up keys with each provider's shape. */
    private val samples = mapOf(
        AiProvider.GEMINI to "AIza" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7_",
        AiProvider.ANTHROPIC to "sk-ant-api03-" + "x".repeat(80) + "-AA",
        AiProvider.OPENROUTER to "sk-or-v1-" + "0123456789abcdef".repeat(4),
        AiProvider.OPENAI to "sk-proj-" + "Ab1_".repeat(30),
        AiProvider.DEEPSEEK to "sk-" + "0123456789abcdef".repeat(2),
        AiProvider.MISTRAL to "aB3dE5gH".repeat(4),
        AiProvider.GROQ to "gsk_" + "Ab12".repeat(13)
    )

    @Test
    fun `each provider recognises its own keys and none of the others`() {
        samples.forEach { (provider, key) ->
            samples.forEach { (other, otherKey) ->
                assertEquals("$provider with a $other key", provider == other, ApiKeyPages.looksLike(provider, otherKey))
            }
            assertTrue(ApiKeyPages.looksLike(provider, "  $key\n"))
        }
    }

    @Test
    fun `ordinary copied text is never taken for a key`() {
        AiProvider.entries.forEach { provider ->
            listOf("", "hola", "200 g de harina", "https://openrouter.ai/settings/keys", "sk-or-v1-abc def").forEach {
                assertFalse("$provider: $it", ApiKeyPages.looksLike(provider, it))
            }
        }
    }

    @Test
    fun `every provider but a custom server has a page to get a key`() {
        AiProvider.entries.forEach { provider ->
            if (provider == AiProvider.CUSTOM) assertNull(ApiKeyPages.pageFor(provider))
            else assertTrue(ApiKeyPages.pageFor(provider)!!.startsWith("https://"))
        }
    }
}
