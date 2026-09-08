package com.bmo00.miga.data.dictation

import com.bmo00.miga.data.vision.AnthropicContentBlock
import com.bmo00.miga.data.vision.AnthropicErrorEnvelope
import com.bmo00.miga.data.vision.AnthropicMessage
import com.bmo00.miga.data.vision.AnthropicRequest
import com.bmo00.miga.data.vision.AnthropicResponse
import com.bmo00.miga.data.vision.describeAnthropicIncompleteResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val TIMEOUT_MILLIS = 30000
private const val CLEANUP_MAX_TOKENS = 512

object AnthropicDictationCleanupClient : DictationCleanupClient {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun cleanUp(rawText: String, apiKey: String, model: String): DictationCleanupResult =
        withContext(Dispatchers.IO) {
            try {
                val prompt = buildDictationCleanupPrompt(rawText)
                val requestBody = json.encodeToString(
                    AnthropicRequest.serializer(),
                    AnthropicRequest(
                        model = model,
                        maxTokens = CLEANUP_MAX_TOKENS,
                        messages = listOf(AnthropicMessage(content = listOf(AnthropicContentBlock(type = "text", text = prompt))))
                    )
                )
                val connection = URL(ANTHROPIC_ENDPOINT).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-api-key", apiKey)
                connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                try {
                    connection.outputStream.use { it.write(requestBody.toByteArray()) }
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        val reason = errorBody?.let {
                            runCatching { json.decodeFromString(AnthropicErrorEnvelope.serializer(), it).error?.message }.getOrNull()
                        }
                        return@withContext DictationCleanupResult.Error(reason ?: "Claude respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(AnthropicResponse.serializer(), body)
                    val text = response.content.firstOrNull { it.type == "text" }?.text
                        ?: return@withContext DictationCleanupResult.Error(describeAnthropicIncompleteResponse(response.stopReason))
                    DictationCleanupResult.Success(stripSurroundingQuotes(text))
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DictationCleanupResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
            }
        }
}
