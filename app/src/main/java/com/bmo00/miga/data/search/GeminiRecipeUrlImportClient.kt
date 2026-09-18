package com.bmo00.miga.data.search

import com.bmo00.miga.data.vision.GeminiContent
import com.bmo00.miga.data.vision.GeminiErrorEnvelope
import com.bmo00.miga.data.vision.GeminiGenerationConfig
import com.bmo00.miga.data.vision.GeminiPart
import com.bmo00.miga.data.vision.GeminiRequest
import com.bmo00.miga.data.vision.GeminiResponse
import com.bmo00.miga.data.vision.RecipeVisionResult
import com.bmo00.miga.data.vision.RecipeVisionResultDto
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

/** Implementación de [RecipeUrlImportClient] contra la API REST de Google Gemini. */
object GeminiRecipeUrlImportClient : RecipeUrlImportClient {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun importFromUrl(url: String, pageText: String, apiKey: String, model: String): RecipeVisionResult =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = json.encodeToString(
                    GeminiRequest.serializer(),
                    GeminiRequest(
                        contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = buildUrlImportPrompt(url, pageText))))),
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
                        return@withContext RecipeVisionResult.Error(reason ?: "Gemini respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(GeminiResponse.serializer(), body)
                    val candidate = response.candidates.firstOrNull()
                    val text = candidate?.content?.parts?.firstOrNull { it.text != null }?.text
                        ?: return@withContext RecipeVisionResult.Error(
                            describeGeminiIncompleteResponse(candidate?.finishReason, response.promptFeedback?.blockReason)
                        )
                    val recipe = try {
                        json.decodeFromString(RecipeVisionResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        val shortReason = e.message?.substringBefore("\nJSON input:") ?: "no se pudo interpretar el JSON"
                        return@withContext RecipeVisionResult.Error("$shortReason\n\nRespuesta completa del modelo:\n$text")
                    }
                    if (recipe.name.isBlank()) {
                        RecipeVisionResult.Error("No se ha reconocido ninguna receta en esa página")
                    } else {
                        RecipeVisionResult.Success(recipe)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecipeVisionResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
            }
        }
}
