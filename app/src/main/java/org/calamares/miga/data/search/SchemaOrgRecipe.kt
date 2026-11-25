package org.calamares.miga.data.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import org.calamares.miga.data.model.PastedLists
import org.calamares.miga.data.vision.RecipeVisionResultDto
import java.net.URL

/** A recipe read from a page's schema.org data, and the address of its photo, if any. */
data class SchemaRecipe(val recipe: RecipeVisionResultDto, val imageUrl: String?) {
    /** Some sites leave the steps out of their standard recipe and only write them in the page. */
    fun hasSteps(): Boolean = recipe.stepGroups.any { group -> group.instructions.any { it.isNotBlank() } }
}

/**
 * Reads the schema.org Recipe (https://schema.org/Recipe) that most recipe websites embed as
 * JSON-LD for search engines: name, ingredients, steps (with their sections), times, servings,
 * cuisine, description and photo. No AI is needed, so it is fast, free and exact; pages without it
 * go to the cooking assistant. Ingredient lines are split into quantity, unit and name like a
 * pasted list (see PastedLists).
 */
object SchemaOrgRecipe {

    private val SCRIPT = Regex("""(?is)<script[^>]*type\s*=\s*["']?application/ld\+json["']?[^>]*>(.*?)</script>""")
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val DURATION = Regex("""(?i)^P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$""")
    private const val MAX_DEPTH = 8

    /** The first Recipe in [html], or null when the page has none with ingredients or steps. */
    private val OG_IMAGE = Regex("""<meta\s+(?:property|name)="og:image"\s+content="([^"]+)"""")

    /** The page's main picture (og:image), for a recipe read from its text. */
    fun pageImage(html: String): String? =
        OG_IMAGE.find(html)?.groupValues?.get(1)?.let(RecipeUrlFetcher::decodeEntities)?.takeIf { it.startsWith("http") }

    fun parse(html: String, pageUrl: String): SchemaRecipe? {
        SCRIPT.findAll(html).forEach { match ->
            val element = runCatching { json.parseToJsonElement(cleanScript(match.groupValues[1])) }.getOrNull() ?: return@forEach
            val recipe = findRecipe(element, 0) ?: return@forEach
            toRecipe(recipe, pageUrl)?.let { return it }
        }
        return null
    }

    /** Some sites wrap the JSON in CDATA or leave raw line breaks inside strings. */
    private fun cleanScript(text: String): String =
        text.trim().removePrefix("<![CDATA[").removeSuffix("]]>").replace(Regex("[\\r\\n\\t]+"), " ")

    private fun findRecipe(element: JsonElement, depth: Int): JsonObject? {
        if (depth > MAX_DEPTH) return null
        return when (element) {
            is JsonArray -> element.firstNotNullOfOrNull { findRecipe(it, depth + 1) }
            is JsonObject -> if (isType(element, "Recipe")) element
                else element.values.firstNotNullOfOrNull { findRecipe(it, depth + 1) }
            else -> null
        }
    }

    private fun isType(obj: JsonObject, type: String): Boolean = when (val value = obj["@type"]) {
        is JsonPrimitive -> value.contentOrNull.equals(type, ignoreCase = true)
        is JsonArray -> value.any { (it as? JsonPrimitive)?.contentOrNull.equals(type, ignoreCase = true) }
        else -> false
    }

    private fun toRecipe(obj: JsonObject, pageUrl: String): SchemaRecipe? {
        val name = text(obj["name"]) ?: return null
        val ingredients = strings(obj["recipeIngredient"] ?: obj["ingredients"])
            .map { PastedLists.ingredient(it) }
            .map { IngredientDto(it.name, it.quantity, it.unit) }
        val stepGroups = steps(obj["recipeInstructions"])
        if (ingredients.isEmpty() && stepGroups.isEmpty()) return null
        val prep = minutes(obj["prepTime"])
        val cook = minutes(obj["cookTime"])
        val recipe = RecipeVisionResultDto(
            name = name,
            categoryName = strings(obj["recipeCategory"]).firstOrNull(),
            prepTimeMinutes = prep,
            // Only a total time: better shown as cooking time than lost.
            cookTimeMinutes = cook ?: minutes(obj["totalTime"]).takeIf { prep == null },
            servings = servings(obj["recipeYield"]) ?: 4,
            notes = text(obj["description"]).orEmpty(),
            source = pageUrl,
            ingredientGroups = if (ingredients.isEmpty()) emptyList() else listOf(IngredientGroupDto(null, ingredients)),
            stepGroups = stepGroups,
            origin = strings(obj["recipeCuisine"]).firstOrNull()
        )
        return SchemaRecipe(recipe, imageUrl(obj["image"], pageUrl))
    }

    /** Text without HTML tags or entities, on one line; null when empty. */
    private fun clean(raw: String): String? =
        RecipeUrlFetcher.decodeEntities(raw.replace(Regex("(?is)<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ").replace(Regex(" ([.,;:!?])"), "$1").trim().takeIf { it.isNotEmpty() }

    private fun text(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> if (element is JsonNull) null else element.contentOrNull?.let { clean(it) }
        is JsonArray -> element.firstNotNullOfOrNull { text(it) }
        is JsonObject -> text(element["name"] ?: element["text"])
        else -> null
    }

    private fun strings(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { text(it) }
        null, is JsonNull -> emptyList()
        // A single text: one per line ("a, b" stays as it is: commas are part of ingredient names).
        is JsonPrimitive -> element.contentOrNull.orEmpty().split(Regex("<br\\s*/?>|\\n")).mapNotNull { clean(it) }
        is JsonObject -> listOfNotNull(text(element))
    }

    /**
     * Steps as text, a list of texts, HowToStep objects or HowToSection objects with their own
     * steps. Steps outside a section form the main group; each section is a group with its name.
     */
    private fun steps(element: JsonElement?): List<StepGroupDto> {
        val main = mutableListOf<String>()
        val sections = mutableListOf<StepGroupDto>()
        fun collect(item: JsonElement?, into: MutableList<String>) {
            when (item) {
                is JsonArray -> item.forEach { collect(it, into) }
                is JsonPrimitive -> if (item !is JsonNull) {
                    // One text with every step: split by paragraphs, list items or lines.
                    item.contentOrNull.orEmpty()
                        .split(Regex("(?i)</p>|</li>|<br\\s*/?>|\\n"))
                        .mapNotNull { clean(it) }
                        .map { PastedLists.lines(it).firstOrNull() ?: it }
                        .forEach { into += it }
                }
                is JsonObject -> when {
                    isType(item, "HowToSection") -> {
                        val steps = mutableListOf<String>()
                        collect(item["itemListElement"], steps)
                        if (steps.isNotEmpty()) sections += StepGroupDto(text(item["name"]), steps)
                    }
                    item["itemListElement"] != null -> collect(item["itemListElement"], into)
                    else -> text(item["text"] ?: item["name"] ?: item["description"])?.let { into += it }
                }
                else -> Unit
            }
        }
        collect(element, main)
        return listOfNotNull(StepGroupDto(null, main).takeIf { main.isNotEmpty() }) + sections
    }

    /** ISO 8601 duration ("PT1H30M", "P0DT45M") in minutes; null when missing or zero. */
    internal fun minutes(element: JsonElement?): Int? {
        val raw = (element as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        val match = DURATION.matchEntire(raw) ?: return null
        val (days, hours, mins, secs) = match.destructured
        val total = (days.toDoubleOrNull() ?: 0.0) * 1440 + (hours.toDoubleOrNull() ?: 0.0) * 60 +
            (mins.toDoubleOrNull() ?: 0.0) + (secs.toDoubleOrNull() ?: 0.0) / 60
        return Math.round(total).toInt().takeIf { it > 0 }
    }

    /** "4", 4, "4 raciones", ["4", "4 servings"]: the first number, within 1–99. */
    private fun servings(element: JsonElement?): Int? {
        val texts = when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(element.contentOrNull)
            else -> emptyList()
        }
        return texts.firstNotNullOfOrNull { Regex("\\d+").find(it)?.value?.toIntOrNull() }?.takeIf { it in 1..99 }
    }

    /** First photo: a URL, a list of them or ImageObjects; relative addresses resolved against the page. */
    private fun imageUrl(element: JsonElement?, pageUrl: String): String? {
        val raw = when (element) {
            is JsonArray -> element.firstNotNullOfOrNull { imageUrl(it, pageUrl) } ?: return null
            is JsonObject -> (element["url"] ?: element["contentUrl"])?.let { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> element.contentOrNull
            else -> null
        } ?: return null
        val resolved = runCatching { URL(URL(pageUrl), raw.trim()).toString() }.getOrNull() ?: return null
        return resolved.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }
}
