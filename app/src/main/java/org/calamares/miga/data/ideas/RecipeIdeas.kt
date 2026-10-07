package org.calamares.miga.data.ideas

import kotlinx.serialization.Serializable
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.ai.outputLanguageInstruction
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.support.AiErrors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val IDEAS_MAX_TOKENS = 6000
/** Recipes described to the AI; beyond this the favourite and most cooked ones are preferred. */
private const val MAX_CATALOG_RECIPES = 300
private const val MAX_INGREDIENTS_PER_RECIPE = 8
/** Previous questions and answers sent along with a follow-up question. */
private const val MAX_HISTORY_TURNS = 3

/** Kind of meal the user is looking for; the weekly menu plans lunch and dinner for a week. */
enum class IdeasMeal { WEEKLY_MENU, BREAKFAST, LUNCH, SNACK, DINNER, STARTER, DESSERT }

/** Style constraints that can be combined. */
enum class IdeasStyle { HEALTHY, LIGHT, QUICK, BUDGET, VEGETARIAN, VEGAN, GLUTEN_FREE, KIDS, SEASONAL }

/** Kinds of dish that can be combined. */
enum class IdeasDish { RICE, PASTA, LEGUMES, VEGETABLES, FISH, MEAT, EGGS, SOUPS, SALADS, BAKING }

/**
 * Options chosen on the Ideas screen, combined into one request ("dinner, healthy, with the air
 * fryer"). [utensils] are free names, such as a saved utensil or a specific model ("Thermomix TM31").
 */
data class IdeasFilters(
    val meal: IdeasMeal? = null,
    val styles: Set<IdeasStyle> = emptySet(),
    val dishes: Set<IdeasDish> = emptySet(),
    val utensils: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = meal == null && styles.isEmpty() && dishes.isEmpty() && utensils.isEmpty()
}

/** One recipe of the user's library the AI points to, with an optional label ("Lunch") and reason. */
data class IdeaRecipe(val recipeId: Long, val label: String?, val reason: String?)

/** A titled group of recipes (a day of the weekly menu, a kind of dish...). */
data class IdeaSection(val title: String, val recipes: List<IdeaRecipe>)

/** A dish the user does not have yet, which can be turned into a recipe with the AI. */
data class NewDishIdea(val name: String, val description: String)

/** The AI answer: an explanation (light Markdown), recipe groups, tips and new dish ideas. */
data class IdeasAnswer(
    val title: String,
    val text: String,
    val sections: List<IdeaSection>,
    val tips: List<String>,
    val newDishes: List<NewDishIdea>
) {
    val recipeIds: List<Long> get() = sections.flatMap { section -> section.recipes.map { it.recipeId } }.distinct()
}

sealed interface IdeasResult {
    data class Success(val answer: IdeasAnswer) : IdeasResult
    data class Error(val reason: String) : IdeasResult
}

/** A previous exchange, sent as context for follow-up questions. */
data class IdeasTurn(val question: String, val answer: IdeasAnswer)

@Serializable
internal data class IdeaRecipeDto(val recipeId: Long = 0, val label: String? = null, val reason: String? = null)

@Serializable
internal data class IdeaSectionDto(val title: String = "", val recipes: List<IdeaRecipeDto> = emptyList())

@Serializable
internal data class NewDishIdeaDto(val name: String = "", val description: String = "")

@Serializable
internal data class IdeasAnswerDto(
    val title: String = "",
    val text: String = "",
    val sections: List<IdeaSectionDto> = emptyList(),
    val tips: List<String> = emptyList(),
    val newDishes: List<NewDishIdeaDto> = emptyList()
)

/**
 * Answers [question] and/or the chosen [filters] using the user's [recipes] as the main source. Recipe ids
 * the model invents are dropped, so every recipe in the answer exists in the library.
 */
suspend fun AiCandidate.askIdeas(
    recipes: List<Recipe>,
    question: String,
    filters: IdeasFilters?,
    history: List<IdeasTurn>,
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault()
): IdeasResult {
    val catalog = selectCatalog(recipes)
    val prompt = buildIdeasPrompt(catalog, question, filters, history, today, locale)
    return when (val result = complete(AiRequest(prompt, IDEAS_MAX_TOKENS))) {
        is AiText.Error -> IdeasResult.Error(result.reason)
        is AiText.Success -> try {
            IdeasResult.Success(decodeAiJson(IdeasAnswerDto.serializer(), result.text).toAnswer(recipes.map { it.id }.toSet()))
        } catch (e: Exception) {
            IdeasResult.Error(AiErrors.badResponse(e, result.text))
        }
    }
}

internal fun IdeasAnswerDto.toAnswer(validIds: Set<Long>): IdeasAnswer = IdeasAnswer(
    title = title.trim(),
    text = text.trim(),
    sections = sections.mapNotNull { section ->
        val recipes = section.recipes
            .filter { it.recipeId in validIds }
            .map { IdeaRecipe(it.recipeId, it.label?.trim()?.ifBlank { null }, it.reason?.trim()?.ifBlank { null }) }
        if (recipes.isEmpty()) null else IdeaSection(section.title.trim(), recipes)
    },
    tips = tips.map { it.trim() }.filter { it.isNotEmpty() },
    newDishes = newDishes.filter { it.name.isNotBlank() }.map { NewDishIdea(it.name.trim(), it.description.trim()) }
)

/** The recipes described to the AI: all of them, or the most relevant when there are too many. */
internal fun selectCatalog(recipes: List<Recipe>): List<Recipe> =
    if (recipes.size <= MAX_CATALOG_RECIPES) {
        recipes
    } else {
        recipes.sortedWith(compareByDescending<Recipe> { it.isFavorite }.thenByDescending { it.timesCooked }.thenByDescending { it.updatedAt })
            .take(MAX_CATALOG_RECIPES)
    }

/** One compact line per recipe: id, name, category, tags, time and main ingredients. */
internal fun describeRecipe(recipe: Recipe): String = buildString {
    append(recipe.id).append(" | ").append(recipe.name)
    recipe.categoryName?.takeIf { it.isNotBlank() }?.let { append(" | category: ").append(it) }
    if (recipe.tags.isNotEmpty()) append(" | tags: ").append(recipe.tags.joinToString(", "))
    recipe.totalTimeMinutes?.let { append(" | ").append(it).append(" min") }
    if (recipe.utensils.isNotEmpty()) append(" | utensils: ").append(recipe.utensils.joinToString(", "))
    if (recipe.isFavorite) append(" | favourite")
    if (recipe.timesCooked > 0) append(" | cooked ").append(recipe.timesCooked).append("x")
    val ingredients = recipe.ingredientGroups.flatMap { it.ingredients }.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
    if (ingredients.isNotEmpty()) append(" | ingredients: ").append(ingredients.take(MAX_INGREDIENTS_PER_RECIPE).joinToString(", "))
}

/** The chosen options as an instruction for the model. */
internal fun IdeasFilters.toInstruction(today: LocalDate): String = buildString {
    when (meal) {
        IdeasMeal.WEEKLY_MENU -> appendLine(
            "Plan a balanced weekly menu from Monday to Sunday with lunch and dinner for each day. Use one " +
                "section per day (title = the day) with the recipes labelled \"Lunch\" or \"Dinner\" (in the answer " +
                "language). Vary the kind of dish (legumes, fish, meat, vegetables, rice, pasta...), avoid repeating a " +
                "recipe and keep dinners lighter than lunches. If the library cannot cover every meal, leave the gap " +
                "and suggest suitable dishes in \"newDishes\"."
        )
        IdeasMeal.BREAKFAST -> appendLine("Suggest breakfasts.")
        IdeasMeal.LUNCH -> appendLine("Suggest dishes for lunch, the main meal of the day.")
        IdeasMeal.SNACK -> appendLine("Suggest afternoon snacks (merienda).")
        IdeasMeal.DINNER -> appendLine("Suggest dinners.")
        IdeasMeal.STARTER -> appendLine("Suggest starters and finger food to share.")
        IdeasMeal.DESSERT -> appendLine("Suggest desserts.")
        null -> appendLine("Suggest recipes.")
    }
    if (styles.isNotEmpty()) {
        val described = styles.map { style ->
            when (style) {
                IdeasStyle.HEALTHY -> "healthy and balanced"
                IdeasStyle.LIGHT -> "light and easy to digest"
                IdeasStyle.QUICK -> "quick (30 minutes or less in total)"
                IdeasStyle.BUDGET -> "cheap, with affordable ingredients"
                IdeasStyle.VEGETARIAN -> "vegetarian"
                IdeasStyle.VEGAN -> "vegan"
                IdeasStyle.GLUTEN_FREE -> "gluten-free"
                IdeasStyle.KIDS -> "that children like"
                IdeasStyle.SEASONAL -> "in season now (" + today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) +
                    "), with seasonal ingredients and suited to the weather"
            }
        }
        appendLine("They must be: " + described.joinToString("; ") + ".")
    }
    if (dishes.isNotEmpty()) {
        val described = dishes.map { dish ->
            when (dish) {
                IdeasDish.RICE -> "rice dishes"
                IdeasDish.PASTA -> "pasta"
                IdeasDish.LEGUMES -> "legumes"
                IdeasDish.VEGETABLES -> "vegetable dishes"
                IdeasDish.FISH -> "fish and seafood"
                IdeasDish.MEAT -> "meat"
                IdeasDish.EGGS -> "egg dishes"
                IdeasDish.SOUPS -> "soups, stews and creams"
                IdeasDish.SALADS -> "salads"
                IdeasDish.BAKING -> "baking and pastry"
            }
        }
        appendLine("Kind of dish: " + described.joinToString(" or ") + ".")
    }
    if (utensils.isNotEmpty()) {
        appendLine(
            "They must be cooked with: " + utensils.joinToString(", ") + ". Prefer library recipes that use it; a " +
                "library recipe that can easily be adapted is also fine if the reason says how (time, temperature, " +
                "speed or programme for that appliance). Take into account the exact model if given."
        )
    }
}.trimEnd()

internal fun buildIdeasPrompt(
    catalog: List<Recipe>,
    question: String,
    filters: IdeasFilters?,
    history: List<IdeasTurn>,
    today: LocalDate,
    locale: Locale
): String = buildString {
    appendLine("You are a friendly cooking assistant inside a recipe app. The user's own recipe library is listed below, one per line as \"id | name | details\".")
    appendLine("Today is ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${today} and the user's country code is \"${locale.country.ifBlank { "unknown" }}\" (use it to know the season and local habits).")
    appendLine()
    appendLine("RECIPE LIBRARY (${catalog.size} recipes):")
    if (catalog.isEmpty()) appendLine("(empty)")
    catalog.forEach { appendLine(describeRecipe(it)) }
    appendLine()
    if (history.isNotEmpty()) {
        appendLine("PREVIOUS CONVERSATION (for context):")
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            appendLine("User: ${turn.question}")
            val recipes = turn.answer.recipeIds.joinToString(", ")
            appendLine("Assistant: ${turn.answer.title}. ${turn.answer.text.take(600)}" + if (recipes.isNotEmpty()) " (recipes: $recipes)" else "")
        }
        appendLine()
    }
    appendLine("REQUEST:")
    filters?.takeIf { !it.isEmpty }?.let { appendLine(it.toInstruction(today)) }
    if (question.isNotBlank()) appendLine(question.trim())
    appendLine()
    appendLine(
        """
        Answer the request. Recommend recipes FROM THE LIBRARY whenever they fit, referring to them only by their
        exact id; never invent ids. Prefer favourites and recipes cooked before when they fit, but keep variety.
        When the request is a question or asks for advice (techniques, substitutions, storage, timing...), answer
        it in "text" and add recipes only if they are relevant. When the library has nothing suitable, say so
        briefly and propose new dishes in "newDishes".

        Return ONLY a compact JSON object (no indentation or line breaks) with exactly this format, with no explanations or extra text:
        {"title": "short title", "text": "main answer, may use **bold**, *italic* and '- ' lists, can be empty",
         "sections": [ { "title": "string", "recipes": [ { "recipeId": number, "label": "string or null", "reason": "short reason or null" } ] } ],
         "tips": ["short practical tip", ...],
         "newDishes": [ { "name": "string", "description": "1 sentence" } ]}
        Use empty lists when a part does not apply. At most 3 tips and 5 new dishes.
        """.trimIndent()
    )
}.trimEnd() + outputLanguageInstruction()
