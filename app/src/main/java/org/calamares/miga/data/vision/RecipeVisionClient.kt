package org.calamares.miga.data.vision

import org.calamares.miga.data.ai.OpenRouterVisionClient
import org.calamares.miga.L10n
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import kotlinx.serialization.Serializable

/**
 * Receta reconocida a partir de una foto. Es un DTO propio, independiente de [org.calamares.miga.
 * data.export.RecipeExportDto] (que tiene su propio versionado de esquema para export/import):
 * aquí no hace falta "uid" ni "recipeBookName" ni "photos", ese contexto lo aporta la pantalla
 * que recibe el resultado. Reutiliza [IngredientGroupDto]/[StepGroupDto] porque su forma es
 * exactamente la que necesita un LLM al describir ingredientes y pasos.
 */
@Serializable
data class RecipeVisionResultDto(
    val name: String,
    val categoryName: String? = null,
    val difficulty: String = "MEDIA",
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int = 4,
    val notes: String = "",
    val source: String = "",
    val ingredientGroups: List<IngredientGroupDto> = emptyList(),
    val stepGroups: List<StepGroupDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val utensils: List<String> = emptyList(),
    /** Fotos del plato terminado encontradas en las imágenes (solo al importar desde fotos). */
    val dishPhotos: List<DishPhotoDto> = emptyList()
)

/**
 * Zona de una de las imágenes enviadas donde aparece una fotografía del plato: [image] es el
 * índice de la imagen (empezando en 0) y [box] es `[ymin, xmin, ymax, xmax]` normalizado de 0 a
 * 1000 (la convención nativa de Gemini para detección de objetos).
 */
@Serializable
data class DishPhotoDto(val image: Int = 0, val box: List<Int> = emptyList())

sealed interface RecipeVisionResult {
    data class Success(val recipe: RecipeVisionResultDto) : RecipeVisionResult
    data class Error(val reason: String) : RecipeVisionResult
}

/** Una imagen (bytes ya en memoria + su mime type) para enviar a un cliente de visión. */
data class VisionImageInput(val bytes: ByteArray, val mimeType: String)

/** Reconoce y estructura el texto de una o varias fotos de una receta usando un LLM con visión.
 *  Cuando se pasa más de una imagen, se asume que todas son páginas/fragmentos de la misma receta. */
interface RecipeVisionClient {
    suspend fun extractRecipe(images: List<VisionImageInput>, apiKey: String, model: String): RecipeVisionResult
}

fun visionClientFor(provider: VisionProviderType): RecipeVisionClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiVisionClient
    VisionProviderType.ANTHROPIC -> AnthropicVisionClient
    VisionProviderType.OPENROUTER -> OpenRouterVisionClient
}

// Prompt compartido entre todos los proveedores de visión: deben pedir exactamente el mismo JSON,
// si no divergirían al cambiar de proveedor en Ajustes.
internal fun recipeExtractionPrompt(): String = """
Eres un asistente que transcribe recetas de cocina a partir de una foto (de un libro, revista o
receta manuscrita, a veces con el texto girado o en columnas). Devuelve
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
  "utensils": ["string", ...],
  "dishPhotos": [ { "image": number, "box": [ymin, xmin, ymax, xmax] } ]
}
Separa cada paso de la elaboración como una instrucción independiente del array "instructions", en
el mismo orden en que aparecen en el texto. Si no puedes determinar algún dato, usa null (o una
lista vacía) en vez de inventarlo. Si no reconoces ninguna receta en la imagen, deja "name" vacío.
Si se incluyen varias imágenes en esta petición, todas son páginas o fragmentos de la MISMA
receta (por ejemplo, fotos consecutivas de un libro de cocina); combina la información de todas
ellas en un único resultado, en el orden en que aparecen las imágenes.
En "dishPhotos" indica dónde hay fotografías del PLATO TERMINADO (la foto que ilustra la receta):
"image" es el índice de la imagen empezando en 0 y "box" son las coordenadas [ymin, xmin, ymax,
xmax] normalizadas de 0 a 1000, ajustadas a la fotografía por dentro, sin márgenes de página,
marcos, bordes, texto, pies de foto ni números de página. Si la imagen entera es una foto del plato
(sin texto de receta), usa la caja que encuadra el plato. No incluyas fotos de pasos intermedios,
ingredientes sueltos, personas ni ilustraciones decorativas. Como máximo 3; si no hay ninguna,
deja la lista vacía.
""".trimIndent() + transcriptionLanguageInstruction()

/**
 * Instrucción que se añade a los prompts que GENERAN texto (no a los que transcriben una receta
 * existente) para que la IA responda en el idioma de la app.
 */
internal fun outputLanguageInstruction(): String {
    val language = if (L10n.locale().language == "es") "español" else "inglés (English)"
    return "\n\nEscribe todos los textos de la respuesta (nombres, descripciones, pasos, notas...) en $language."
}

/**
 * Instrucción para los prompts que TRANSCRIBEN una receta existente (foto, página web): la receta
 * se guarda en el idioma de la app, traduciéndola si el original está en otro idioma.
 */
internal fun transcriptionLanguageInstruction(): String {
    val language = if (L10n.locale().language == "es") "español" else "inglés (English)"
    return "\n\nEscribe la receta en $language. Si el original está en otro idioma, tradúcela entera " +
        "(nombre, categoría, ingredientes, unidades, pasos, notas, etiquetas y utensilios) de forma natural; " +
        "si ya está en $language, transcríbela tal cual. No cambies las cantidades ni los valores de \"difficulty\"."
}

// Algunos proveedores envuelven el JSON en un bloque de markdown pese a pedir JSON puro; se lo
// quitamos antes de parsear. Compartida entre los clientes de Gemini y Anthropic (visión y salud).
internal fun stripMarkdownFences(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("```")) return trimmed
    return trimmed
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
}
