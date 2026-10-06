package org.calamares.miga.data.ai

import org.calamares.miga.L10n

/** JSON format of every prompt that produces a full recipe; decoded into RecipeVisionResultDto. */
internal const val RECIPE_JSON_FORMAT = """{
  "name": "string",
  "categoryName": "string or null",
  "difficulty": "EASY" | "MEDIUM" | "HARD",
  "prepTimeMinutes": whole number of minutes or null,
  "cookTimeMinutes": whole number of minutes or null,
  "servings": whole number,
  "notes": "string",
  "source": "string",
  "ingredientGroups": [ { "name": "string or null", "ingredients": [ { "name": "string", "quantity": number or null, "unit": "string or null" } ] } ],
  "stepGroups": [ { "name": "string or null", "instructions": ["string", ...] } ],
  "tags": ["string", ...],
  "utensils": ["string", ...]
}"""

private fun appLanguageName(): String = if (L10n.locale().language == "es") "Spanish" else "English"

/** Appended to prompts that GENERATE text so the model answers in the app language. */
internal fun outputLanguageInstruction(): String =
    "\n\nWrite every text in the response (names, descriptions, steps, notes...) in ${appLanguageName()}."

/**
 * Appended to prompts that TRANSCRIBE an existing recipe (photo, web page): the recipe is stored
 * in the app language and translated when the original is in another language.
 */
internal fun transcriptionLanguageInstruction(): String {
    val language = appLanguageName()
    return "\n\nWrite the recipe in $language. If the original is in another language, translate all of it " +
        "(name, category, ingredients, units, steps, notes, tags and utensils) naturally; " +
        "if it is already in $language, transcribe it as is. Do not change quantities or the \"difficulty\" values."
}
