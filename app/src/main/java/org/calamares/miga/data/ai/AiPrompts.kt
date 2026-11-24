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
  "ingredientGroups": [ { "name": "string or null", "ingredients": [ { "name": "string", "quantity": number or null, "unit": "string or null", "optional": true or false } ] } ],
  "stepGroups": [ { "name": "string or null", "instructions": ["string", ...] } ],
  "tags": ["string", ...],
  "origin": "place or cuisine it comes from (a country, region or city such as Mexico, Córdoba or Japan), only when the recipe or its source clearly says so or it is a well-known dish of that place; otherwise null",
  "originCountry": "ISO 3166-1 alpha-2 code of the origin's country (MX, ES, JP...) or null",
  "optionalIngredients": "rule: an ingredient the recipe says can be left out (optional, if you like, for garnish) has optional true, and that note is not written in its name",
  "utensils": ["appliance or special equipment the recipe needs (oven, air fryer, food processor, pressure cooker...); never basics every kitchen has such as knife, pot, pan, bowl or fridge", ...]
}"""

/** One tip in the answers of the prompts that import a link (see TIP_INSTEAD_OF_RECIPE). */
private const val TIP_JSON = """{"category": "TECHNIQUES" | "PRESERVING" | "LEFTOVERS" | "SUBSTITUTIONS" | "SAFETY" | "BAKING" | "TIME_SAVING" | "KITCHEN", "title": "short title of the tip", "body": "the tip itself in 1 to 4 clear sentences, with the useful details (times, amounts)", "keywords": ["2 to 5 ingredients, techniques or tools it is about", ...]}"""

/**
 * Added to the prompts that import a link (a page, a post, a video): what was shared may be a
 * cooking tip rather than a recipe, and then the tip is returned instead (see ImportedTip).
 */
internal const val TIP_INSTEAD_OF_RECIPE = """
If the content is not a recipe but a cooking tip or trick (a technique, how to keep, choose or
prepare an ingredient, a kitchen hack, how to fix a mistake, how to improve or adapt a dish in
general), do not make up a recipe: return instead only this JSON object, written in the same
language as the rest of your answer must be. An article that explains how to make a dish better,
healthier or easier without giving its list of ingredients with quantities is a tip, even if it
names some ingredients; a recipe has its ingredients and steps.
{"name": "", "tip": $TIP_JSON}
When there is a recipe and the content also gives cooking tips that are useful beyond this recipe
(how to peel prawns quickly, how to keep garlic from burning, how to store the leftovers), add to
the recipe's JSON object "tips": [$TIP_JSON, ...] with at most 3 of them; advice that only matters
for this recipe goes in its "notes" instead. Leave "tips" out when there are none."""


/** Language the AI must write in: the app's, "es" or "en". */
internal fun appLanguageCode(): String = if (L10n.locale().language == "es") "es" else "en"

internal fun appLanguageName(code: String = appLanguageCode()): String = if (code == "es") "Spanish" else "English"

/** Appended to prompts that GENERATE text so the model answers in the app language. */
internal fun outputLanguageInstruction(): String =
    "\n\nWrite every text in the response (names, descriptions, steps, notes...) in ${appLanguageName()}."

/**
 * Appended to prompts that TRANSCRIBE an existing recipe (photo, web page): the recipe is stored
 * in the app language and translated when the original is in another language. Spelled out with
 * care because the rest of the prompt is in English, which some models took as the language to
 * answer in.
 */
internal fun transcriptionLanguageInstruction(): String {
    val language = appLanguageName()
    return "\n\nLANGUAGE (mandatory): the user reads the app in $language. Write every text value of the JSON " +
        "(name, category, ingredients, units, steps, notes, tags and utensils) in $language, even though these " +
        "instructions are in English. If the original is already in $language, transcribe it as is, without " +
        "translating anything; if it is in another language, translate all of it naturally into $language. " +
        "Do not change quantities or the \"difficulty\" values."
}

/** Added when a recipe came back in another language; see completeInAppLanguage. */
internal fun languageCorrectionInstruction(wrong: String): String {
    val language = appLanguageName()
    return "\n\nIMPORTANT: a previous answer to this request was written in ${appLanguageName(wrong)}, which is wrong. " +
        "Write the whole recipe in $language: every name, ingredient, unit, step, note and tag."
}

/** The categories and kitchen equipment the user already has, offered to the model to reuse. */
data class KnownLabels(val categories: List<String>, val equipment: List<String>) {
    companion object {
        val NONE = KnownLabels(emptyList(), emptyList())
    }
}

private const val MAX_KNOWN_LABELS = 80

/**
 * Asks the model to pick the user's existing category and equipment when one fits, so recipes are
 * filed where the user expects and near duplicates ("Postre" next to "Postres") are not created.
 */
internal fun knownLabelsInstruction(known: KnownLabels): String = buildString {
    if (known.categories.isNotEmpty()) {
        append("\n\nThe user already has these recipe categories: ")
        append(known.categories.take(MAX_KNOWN_LABELS).joinToString(", ") { "\"$it\"" })
        append(". For \"categoryName\" use the one that best fits the recipe, written exactly as above; only if none fits, propose a short new category.")
    }
    if (known.equipment.isNotEmpty()) {
        append("\n\nThe user already has this kitchen equipment: ")
        append(known.equipment.take(MAX_KNOWN_LABELS).joinToString(", ") { "\"$it\"" })
        append(". In \"utensils\" use these names, written exactly as above, for the equipment they cover; add a new name only for equipment none of them covers.")
    }
}
