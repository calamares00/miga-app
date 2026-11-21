package org.calamares.miga.data.ai

import org.calamares.miga.data.search.SocialPost
import org.calamares.miga.data.search.buildPostAudioPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which providers take a video's audio, and how it is sent. */
class AudioTranscriptionTest {

    private fun candidate(provider: AiProvider) = AiCandidate(provider, "key", "model")

    @Test
    fun `gemini listens, openai groq and mistral transcribe, the rest cannot`() {
        assertEquals(AudioSupport.LISTENS, candidate(AiProvider.GEMINI).audioSupport)
        listOf(AiProvider.OPENAI, AiProvider.GROQ, AiProvider.MISTRAL).forEach {
            assertEquals(it.name, AudioSupport.TRANSCRIBES, candidate(it).audioSupport)
            assertTrue(it.name, transcriptionService(it)!!.first.endsWith("/audio/transcriptions"))
        }
        listOf(AiProvider.ANTHROPIC, AiProvider.DEEPSEEK, AiProvider.OPENROUTER, AiProvider.CUSTOM).forEach {
            assertNull(it.name, candidate(it).audioSupport)
            assertNull(it.name, transcriptionService(it))
        }
    }

    @Test
    fun `multipart body with the model and the file`() {
        val body = String(multipartBody("B", mapOf("model" to "whisper"), "file", "audio.m4a", "audio/mp4", byteArrayOf(65, 66)), Charsets.ISO_8859_1)
        assertEquals(
            "--B\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper\r\n" +
                "--B\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\nContent-Type: audio/mp4\r\n\r\nAB\r\n--B--\r\n",
            body
        )
    }

    @Test
    fun `gemini request carries the audio before the prompt`() {
        val request = GeminiRequest(
            listOf(GeminiContent(listOf(GeminiPart(inlineData = GeminiInlineData("audio/aac", "AAA")), GeminiPart(text = "hola")))),
            GeminiGenerationConfig()
        )
        val body = aiJson.encodeToString(GeminiRequest.serializer(), request)
        assertTrue(body, body.contains("\"inline_data\":{\"mime_type\":\"audio/aac\",\"data\":\"AAA\"}"))
    }

    @Test
    fun `the prompt has the caption and, when transcribed, what is said`() {
        val post = SocialPost("Salmón a la naranja", null, "@cocina", "https://www.instagram.com/cocina/reel/X/", "Instagram")
        val transcribed = buildPostAudioPrompt(post, "Ponemos 200 gramos de salmón")
        assertTrue(transcribed.contains("Ponemos 200 gramos de salmón"))
        assertTrue(transcribed.contains("Salmón a la naranja"))
        assertTrue(transcribed.contains("https://www.instagram.com/cocina/reel/X/"))
        val listened = buildPostAudioPrompt(post, null)
        assertTrue(listened.contains("audio of the video is attached"))
        assertFalse(listened.contains("Transcript"))
    }
}
