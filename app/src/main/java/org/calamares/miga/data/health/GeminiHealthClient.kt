package org.calamares.miga.data.health

import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.vision.GeminiContent
import org.calamares.miga.data.vision.GeminiErrorEnvelope
import org.calamares.miga.data.vision.GeminiGenerationConfig
import org.calamares.miga.data.vision.GeminiPart
import org.calamares.miga.data.vision.GeminiRequest
import org.calamares.miga.data.vision.GeminiResponse
import org.calamares.miga.data.vision.describeGeminiIncompleteResponse
import org.calamares.miga.data.vision.stripMarkdownFences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val GEMINI_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
private const val TIMEOUT_MILLIS = 30000

/** Implementación de [RecipeHealthClient] contra la API REST de Google Gemini (generateContent). */
object GeminiHealthClient : RecipeHealthClient {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun analyzeHealthiness(ingredientsText: String, stepsText: String, apiKey: String, model: String): RecipeHealthResult =
        withContext(Dispatchers.IO) {
            try {
                val prompt = buildHealthPrompt(ingredientsText, stepsText)
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
                        return@withContext RecipeHealthResult.Error(AiErrors.http("Gemini", responseCode, reason))
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(GeminiResponse.serializer(), body)
                    val candidate = response.candidates.firstOrNull()
                    val text = candidate?.content?.parts?.firstOrNull { it.text != null }?.text
                        ?: return@withContext RecipeHealthResult.Error(
                            describeGeminiIncompleteResponse(candidate?.finishReason, response.promptFeedback?.blockReason)
                        )
                    val resultDto = try {
                        json.decodeFromString(RecipeHealthResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        return@withContext RecipeHealthResult.Error(AiErrors.badResponse(e, text))
                    }
                    val colorLevel = runCatching { HealthColorLevel.valueOf(resultDto.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
                    RecipeHealthResult.Success(colorLevel, resultDto.description)
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecipeHealthResult.Error(AiErrors.exception(e))
            }
        }
}
