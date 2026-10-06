package org.calamares.miga.data.search

import org.calamares.miga.data.ai.OpenRouterRecipeUrlImportClient
import org.calamares.miga.data.vision.transcriptionLanguageInstruction
import org.calamares.miga.data.vision.RECIPE_JSON_FORMAT
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.VisionProviderType

/**
 * Extrae una receta (mismo formato que el reconocimiento por foto, ver
 * [org.calamares.miga.data.vision.RecipeVisionResultDto]) a partir del texto legible de una página web
 * ya descargada (ver [RecipeUrlFetcher]) - el LLM interpreta el texto, sin parseo estructurado de
 * microdatos (schema.org Recipe, JSON-LD...) de por medio.
 */
interface RecipeUrlImportClient {
    suspend fun importFromUrl(url: String, pageText: String, apiKey: String, model: String): RecipeVisionResult
}

fun recipeUrlImportClientFor(provider: VisionProviderType): RecipeUrlImportClient = when (provider) {
    VisionProviderType.GEMINI -> GeminiRecipeUrlImportClient
    VisionProviderType.ANTHROPIC -> AnthropicRecipeUrlImportClient
    VisionProviderType.OPENROUTER -> OpenRouterRecipeUrlImportClient
}

/** Extracts the main recipe from a web page's text using the same JSON format as photo transcription. */
internal fun buildUrlImportPrompt(url: String, pageText: String): String = """
You are a cooking assistant. Below is the text extracted from a web page ($url) that should contain
a recipe. The text may include noise unrelated to the recipe (navigation menus, ads, comments from
other users, links to other recipes...); ignore it and keep only the main recipe of the page.

Page text:
---
$pageText
---

Return ONLY a JSON object with exactly this format, with no explanations or extra text:
$RECIPE_JSON_FORMAT
If the text does not contain a recognisable recipe, leave "name" empty. Put the original URL ($url)
in "source". If you cannot determine a value, use null (or an empty list) instead of making it up.
""".trimIndent() + transcriptionLanguageInstruction()
