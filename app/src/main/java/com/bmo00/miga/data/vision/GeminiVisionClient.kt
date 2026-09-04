package com.bmo00.miga.data.vision

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    private val json = Json { ignoreUnknownKeys = true }

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
                    val recipe = json.decodeFromString(RecipeVisionResultDto.serializer(), text)
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

@Serializable
private data class GeminiRequest(val contents: List<GeminiContent>, val generationConfig: GeminiGenerationConfig)

@Serializable
private data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
private data class GeminiPart(
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null,
    val text: String? = null
)

@Serializable
private data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String
)

@Serializable
private data class GeminiGenerationConfig(val responseMimeType: String = "application/json")

@Serializable
private data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
private data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
private data class GeminiErrorEnvelope(val error: GeminiErrorDetail? = null)

@Serializable
private data class GeminiErrorDetail(val message: String? = null)
