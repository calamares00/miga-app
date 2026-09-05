package com.bmo00.miga.data.health

import com.bmo00.miga.data.model.HealthColorLevel
import com.bmo00.miga.data.vision.GeminiContent
import com.bmo00.miga.data.vision.GeminiErrorEnvelope
import com.bmo00.miga.data.vision.GeminiGenerationConfig
import com.bmo00.miga.data.vision.GeminiPart
import com.bmo00.miga.data.vision.GeminiRequest
import com.bmo00.miga.data.vision.GeminiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
                val prompt = buildPrompt(ingredientsText, stepsText)
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
                        return@withContext RecipeHealthResult.Error(reason ?: "Gemini respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(GeminiResponse.serializer(), body)
                    val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
                        ?: return@withContext RecipeHealthResult.Error("Gemini no devolvió ningún resultado")
                    val resultDto = try {
                        json.decodeFromString(RecipeHealthResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        val shortReason = e.message?.substringBefore("\nJSON input:") ?: "no se pudo interpretar el JSON"
                        return@withContext RecipeHealthResult.Error(shortReason)
                    }
                    val colorLevel = runCatching { HealthColorLevel.valueOf(resultDto.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
                    RecipeHealthResult.Success(colorLevel, resultDto.description)
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecipeHealthResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
            }
        }

    private fun buildPrompt(ingredientsText: String, stepsText: String): String = """
Eres un asistente que evalúa lo saludable que es una receta a partir de sus ingredientes y su
forma de cocinado.

Ingredientes:
$ingredientsText

Preparación:
$stepsText

Devuelve ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional:
{ "colorLevel": "GREEN" | "YELLOW" | "RED", "description": "string, 2-4 frases explicando por qué" }
GREEN = receta equilibrada y saludable. YELLOW = moderada (algún exceso de grasa, azúcar o sal,
procesados, fritos ocasionales). RED = poco saludable (frituras, mucho azúcar o grasa saturada,
ultraprocesados, sin verdura ni proteína magra). Basa el análisis solo en lo indicado, sin inventar
datos nutricionales exactos.
""".trimIndent()

    // A veces Gemini envuelve el JSON en un bloque de markdown pese a pedirle
    // responseMimeType = "application/json"; se lo quitamos antes de parsear.
    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}

@Serializable
private data class RecipeHealthResultDto(val colorLevel: String = "YELLOW", val description: String = "")
