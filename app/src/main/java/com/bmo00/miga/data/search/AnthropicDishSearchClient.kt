package com.bmo00.miga.data.search

import com.bmo00.miga.data.vision.AnthropicContentBlock
import com.bmo00.miga.data.vision.AnthropicErrorEnvelope
import com.bmo00.miga.data.vision.AnthropicMessage
import com.bmo00.miga.data.vision.AnthropicRequest
import com.bmo00.miga.data.vision.AnthropicResponse
import com.bmo00.miga.data.vision.describeAnthropicIncompleteResponse
import com.bmo00.miga.data.vision.stripMarkdownFences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val TIMEOUT_MILLIS = 30000
private const val SEARCH_MAX_TOKENS = 2048

/** Implementación de [DishSearchClient] contra la API de Mensajes de Anthropic (Claude). */
object AnthropicDishSearchClient : DishSearchClient {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun searchDishes(query: String, apiKey: String, model: String): DishSearchResult =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = json.encodeToString(
                    AnthropicRequest.serializer(),
                    AnthropicRequest(
                        model = model,
                        maxTokens = SEARCH_MAX_TOKENS,
                        messages = listOf(AnthropicMessage(content = listOf(AnthropicContentBlock(type = "text", text = buildDishSearchPrompt(query)))))
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
                        return@withContext DishSearchResult.Error(reason ?: "Claude respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(AnthropicResponse.serializer(), body)
                    val text = response.content.firstOrNull { it.type == "text" }?.text
                        ?: return@withContext DishSearchResult.Error(describeAnthropicIncompleteResponse(response.stopReason))
                    val resultDto = try {
                        json.decodeFromString(DishSearchResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        val shortReason = e.message?.substringBefore("\nJSON input:") ?: "no se pudo interpretar el JSON"
                        return@withContext DishSearchResult.Error(shortReason)
                    }
                    val dishes = resultDto.dishes.filter { it.name.isNotBlank() }.map { DishSuggestion(it.name, it.description, it.origin) }
                    DishSearchResult.Success(dishes)
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DishSearchResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
            }
        }
}
