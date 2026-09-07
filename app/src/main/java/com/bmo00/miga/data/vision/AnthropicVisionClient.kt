package com.bmo00.miga.data.vision

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

private const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val TIMEOUT_MILLIS = 30000
private const val VISION_MAX_TOKENS = 8192

/** Implementación de [RecipeVisionClient] contra la API de Mensajes de Anthropic (Claude). */
object AnthropicVisionClient : RecipeVisionClient {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun extractRecipe(images: List<VisionImageInput>, apiKey: String, model: String): RecipeVisionResult =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext RecipeVisionResult.Error("No hay ninguna foto que procesar")
            try {
                val requestBody = json.encodeToString(
                    AnthropicRequest.serializer(),
                    AnthropicRequest(
                        model = model,
                        maxTokens = VISION_MAX_TOKENS,
                        messages = listOf(
                            AnthropicMessage(
                                content = images.map {
                                    AnthropicContentBlock(
                                        type = "image",
                                        source = AnthropicImageSource(mediaType = it.mimeType, data = Base64.getEncoder().encodeToString(it.bytes))
                                    )
                                } + AnthropicContentBlock(type = "text", text = RECIPE_EXTRACTION_PROMPT)
                            )
                        )
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
                        return@withContext RecipeVisionResult.Error(reason ?: "Claude respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(AnthropicResponse.serializer(), body)
                    val text = response.content.firstOrNull { it.type == "text" }?.text
                        ?: return@withContext RecipeVisionResult.Error(describeAnthropicIncompleteResponse(response.stopReason))
                    val recipe = try {
                        json.decodeFromString(RecipeVisionResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        val shortReason = e.message?.substringBefore("\nJSON input:") ?: "no se pudo interpretar el JSON"
                        return@withContext RecipeVisionResult.Error("$shortReason\n\nRespuesta completa del modelo:\n$text")
                    }
                    if (recipe.name.isBlank()) {
                        RecipeVisionResult.Error("No se ha reconocido ninguna receta en la foto")
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
