package org.calamares.miga.data.search

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.vision.AnthropicContentBlock
import org.calamares.miga.data.vision.AnthropicErrorEnvelope
import org.calamares.miga.data.vision.AnthropicMessage
import org.calamares.miga.data.vision.AnthropicRequest
import org.calamares.miga.data.vision.AnthropicResponse
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.RecipeVisionResultDto
import org.calamares.miga.data.vision.describeAnthropicIncompleteResponse
import org.calamares.miga.data.vision.stripMarkdownFences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val TIMEOUT_MILLIS = 30000
private const val RECIPE_MAX_TOKENS = 8192

/** Implementación de [DishRecipeGenerationClient] contra la API de Mensajes de Anthropic (Claude). */
object AnthropicDishRecipeGenerationClient : DishRecipeGenerationClient {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun generateRecipe(dish: DishSuggestion, apiKey: String, model: String): RecipeVisionResult =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = json.encodeToString(
                    AnthropicRequest.serializer(),
                    AnthropicRequest(
                        model = model,
                        maxTokens = RECIPE_MAX_TOKENS,
                        messages = listOf(AnthropicMessage(content = listOf(AnthropicContentBlock(type = "text", text = buildDishRecipePrompt(dish)))))
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
                        return@withContext RecipeVisionResult.Error(reason ?: L10n.str(R.string.claude_respondio_codigo_x, responseCode))
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(AnthropicResponse.serializer(), body)
                    val text = response.content.firstOrNull { it.type == "text" }?.text
                        ?: return@withContext RecipeVisionResult.Error(describeAnthropicIncompleteResponse(response.stopReason))
                    val recipe = try {
                        json.decodeFromString(RecipeVisionResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        val shortReason = e.message?.substringBefore("\nJSON input:") ?: L10n.str(R.string.no_pudo_interpretar_json)
                        return@withContext RecipeVisionResult.Error(L10n.str(R.string.x_respuesta_completa_modelo_x, shortReason, text))
                    }
                    if (recipe.name.isBlank()) {
                        RecipeVisionResult.Error(L10n.str(R.string.no_ha_podido_generar_receta))
                    } else {
                        RecipeVisionResult.Success(recipe)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecipeVisionResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.error_desconocido))
            }
        }
}
