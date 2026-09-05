package com.bmo00.miga.data.vision

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

private const val EXTRACTION_PROMPT = """
Eres un asistente que transcribe recetas de cocina a partir de una foto (de un libro, revista o
receta manuscrita, normalmente en español, a veces con el texto girado o en columnas). Devuelve
ÚNICAMENTE un JSON con este formato exacto, sin explicaciones ni texto adicional:
{
  "name": "string",
  "categoryName": "string o null",
  "difficulty": "FACIL" | "MEDIA" | "DIFICIL",
  "prepTimeMinutes": number o null,
  "cookTimeMinutes": number o null,
  "servings": number,
  "notes": "string",
  "source": "string",
  "ingredientGroups": [ { "name": "string o null", "ingredients": [ { "name": "string", "quantity": number o null, "unit": "string o null" } ] } ],
  "stepGroups": [ { "name": "string o null", "instructions": ["string", ...] } ],
  "tags": ["string", ...],
  "utensils": ["string", ...]
}
Separa cada paso de la elaboración como una instrucción independiente del array "instructions", en
el mismo orden en que aparecen en el texto. Si no puedes determinar algún dato, usa null (o una
lista vacía) en vez de inventarlo. Si no reconoces ninguna receta en la imagen, deja "name" vacío.
"""

/** Implementación de [RecipeVisionClient] contra la API REST de Google Gemini (generateContent). */
object GeminiVisionClient : RecipeVisionClient {

    // coerceInputValues: Gemini a veces pone null en campos de texto opcionales (p.ej. "source")
    // que en el DTO son String no nulo con valor por defecto; sin esto el parseo falla entero.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun extractRecipe(imageBytes: ByteArray, mimeType: String, apiKey: String, model: String): RecipeVisionResult =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = json.encodeToString(
                    GeminiRequest.serializer(),
                    GeminiRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = listOf(
                                    GeminiPart(inlineData = GeminiInlineData(mimeType, Base64.getEncoder().encodeToString(imageBytes))),
                                    GeminiPart(text = EXTRACTION_PROMPT)
                                )
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
                        return@withContext RecipeVisionResult.Error(reason ?: "Gemini respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = json.decodeFromString(GeminiResponse.serializer(), body)
                    val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
                        ?: return@withContext RecipeVisionResult.Error("Gemini no devolvió ningún resultado")
                    val recipe = try {
                        json.decodeFromString(RecipeVisionResultDto.serializer(), stripMarkdownFences(text))
                    } catch (e: Exception) {
                        // kotlinx.serialization recorta el fragmento de JSON de su propio mensaje a un
                        // puñado de caracteres (ver JsonExceptionsKt.minify); nos quedamos solo con la
                        // parte descriptiva y adjuntamos el texto completo de Gemini aparte, sin recortar.
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
