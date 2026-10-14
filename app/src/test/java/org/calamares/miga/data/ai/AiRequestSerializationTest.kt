package org.calamares.miga.data.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fields with a default value that the APIs require (role, image type...) must be sent. */
class AiRequestSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `openrouter messages include the role and omit max_tokens when unset`() {
        val body = json.encodeToString(
            OpenRouterRequest.serializer(),
            OpenRouterRequest(model = "m", messages = listOf(OpenRouterMessage(content = listOf(OpenRouterPart(type = "text", text = "hola")))))
        )
        assertTrue(body, body.contains("\"role\":\"user\""))
        assertFalse(body, body.contains("max_tokens"))
    }

    @Test
    fun `anthropic messages include the role and the image source type`() {
        val body = json.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = "m",
                maxTokens = 10,
                messages = listOf(
                    AnthropicMessage(content = listOf(AnthropicContentBlock(type = "image", source = AnthropicImageSource(mediaType = "image/jpeg", data = "x"))))
                )
            )
        )
        assertTrue(body, body.contains("\"role\":\"user\""))
        assertTrue(body, body.contains("\"type\":\"base64\""))
    }

    @Test
    fun `gemini asks for json responses`() {
        val body = json.encodeToString(GeminiGenerationConfig.serializer(), GeminiGenerationConfig())
        assertTrue(body, body.contains("application/json"))
    }
}
