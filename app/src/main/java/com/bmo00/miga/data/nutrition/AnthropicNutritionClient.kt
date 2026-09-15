package com.bmo00.miga.data.nutrition

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
private const val NUTRITION_MAX_TOKENS = 1024

/** Implementación de [RecipeNutritionClient] contra la API de Mensajes de Anthropic (Claude). */
object AnthropicNutritionClient : RecipeNutritionClient {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun analyzeNutrition(ingredientsText: String, stepsText: String, servings: Int, apiKey: String, model: String): RecipeNutritionResult =
        withContext(Dispatchers.IO) {
            try {
                val prompt = buildNutritionPrompt(ingredientsText, stepsText, servings)
                val requestBody = json.encodeToString(
                    AnthropicRequest.serializer(),
                    AnthropicRequest(
                        model = model,
                        maxTokens = NUTRITION_MAX_TOKENS,
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
                        return@withContext RecipeNutritionResult.Error(reason ?: "Claude respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(AnthropicResponse.serializer(), body)
                    val text = response.content.firstOrNull { it.type == "text" }?.text
                        ?: return@withContext RecipeNutritionResult.Error(describeAnthropicIncompleteResponse(response.stopReason))
                    val resultDto = try {
                        json.decodeFromString(RecipeNutritionResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        val shortReason = e.message?.substringBefore("\nJSON input:") ?: "no se pudo interpretar el JSON"
                        return@withContext RecipeNutritionResult.Error(shortReason)
                    }
                    RecipeNutritionResult.Success(resultDto.caloriesPerServing, resultDto.proteinGrams, resultDto.carbsGrams, resultDto.fatGrams)
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecipeNutritionResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
            }
        }
}
