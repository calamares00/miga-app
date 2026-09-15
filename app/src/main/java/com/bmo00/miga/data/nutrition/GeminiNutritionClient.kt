package com.bmo00.miga.data.nutrition

import com.bmo00.miga.data.vision.GeminiContent
import com.bmo00.miga.data.vision.GeminiErrorEnvelope
import com.bmo00.miga.data.vision.GeminiGenerationConfig
import com.bmo00.miga.data.vision.GeminiPart
import com.bmo00.miga.data.vision.GeminiRequest
import com.bmo00.miga.data.vision.GeminiResponse
import com.bmo00.miga.data.vision.describeGeminiIncompleteResponse
import com.bmo00.miga.data.vision.stripMarkdownFences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val GEMINI_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
private const val TIMEOUT_MILLIS = 30000

/** Implementación de [RecipeNutritionClient] contra la API REST de Google Gemini (generateContent). */
object GeminiNutritionClient : RecipeNutritionClient {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun analyzeNutrition(ingredientsText: String, stepsText: String, servings: Int, apiKey: String, model: String): RecipeNutritionResult =
        withContext(Dispatchers.IO) {
            try {
                val prompt = buildNutritionPrompt(ingredientsText, stepsText, servings)
                val requestBody = json.encodeToString(
                    GeminiRequest.serializer(),
                    GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                        generationConfig = GeminiGenerationConfig()
                    )
                )
                val endpoint = "$GEMINI_ENDPOINT_BASE/$model:generateContent"
                val connection = URL("$endpoint?key=$apiKey").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                try {
                    connection.outputStream.use { it.write(requestBody.toByteArray()) }
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        val reason = errorBody?.let {
                            runCatching { json.decodeFromString(GeminiErrorEnvelope.serializer(), it).error?.message }.getOrNull()
                        }
                        return@withContext RecipeNutritionResult.Error(reason ?: "Gemini respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(GeminiResponse.serializer(), body)
                    val candidate = response.candidates.firstOrNull()
                    val text = candidate?.content?.parts?.firstOrNull { it.text != null }?.text
                        ?: return@withContext RecipeNutritionResult.Error(
                            describeGeminiIncompleteResponse(candidate?.finishReason, response.promptFeedback?.blockReason)
                        )
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
