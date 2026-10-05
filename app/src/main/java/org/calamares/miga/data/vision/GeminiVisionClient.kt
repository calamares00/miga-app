package org.calamares.miga.data.vision

import org.calamares.miga.data.support.AiErrors
import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

// El modelo ya no es fijo: lo elige el usuario en Ajustes (ver GeminiModels.kt) porque Google
// renueva este catálogo con el tiempo y un id fijo en el código se queda obsoleto (como le pasó
// a "gemini-2.0-flash"). Si una llamada empieza a fallar con 404, es que el modelo elegido ya
// no existe; comprobar el vigente en https://ai.google.dev/gemini-api/docs/models.
private const val GEMINI_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
private const val TIMEOUT_MILLIS = 30000

/** Implementación de [RecipeVisionClient] contra la API REST de Google Gemini (generateContent). */
object GeminiVisionClient : RecipeVisionClient {

    // coerceInputValues: Gemini a veces pone null en campos de texto opcionales (p.ej. "source")
    // que en el DTO son String no nulo con valor por defecto; sin esto el parseo falla entero.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun extractRecipe(images: List<VisionImageInput>, apiKey: String, model: String): RecipeVisionResult =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext RecipeVisionResult.Error(L10n.str(R.string.no_hay_ninguna_foto_procesar))
            try {
                val requestBody = json.encodeToString(
                    GeminiRequest.serializer(),
                    GeminiRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = images.map { GeminiPart(inlineData = GeminiInlineData(it.mimeType, Base64.getEncoder().encodeToString(it.bytes))) } +
                                    GeminiPart(text = RECIPE_EXTRACTION_PROMPT)
                            )
                        ),
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
                        return@withContext RecipeVisionResult.Error(AiErrors.http("Gemini", responseCode, reason))
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
                        // kotlinx.serialization recorta el fragmento de JSON de su propio mensaje a un
                        // puñado de caracteres (ver JsonExceptionsKt.minify); nos quedamos solo con la
                        // parte descriptiva y adjuntamos el texto completo de Gemini aparte, sin recortar.
                        return@withContext RecipeVisionResult.Error(AiErrors.badResponse(e, text))
                    }
                    if (recipe.name.isBlank()) {
                        RecipeVisionResult.Error(L10n.str(R.string.no_ha_reconocido_ninguna_receta_2))
                    } else {
                        RecipeVisionResult.Success(recipe)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecipeVisionResult.Error(AiErrors.exception(e))
            }
        }
}
