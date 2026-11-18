package org.calamares.miga.data.allergens

import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.searchKey

/** What one ingredient brings. */
data class IngredientAllergens(
    val levels: Map<Allergen, AllergenLevel>,
    /** Allergens it does not have but may carry as traces (see AllergenRule.traces). */
    val traces: Set<Allergen> = emptySet()
) {
    val contains: Boolean get() = levels.values.any { it == AllergenLevel.CONTAINS }
    val mayContain: Boolean get() = levels.values.any { it == AllergenLevel.MAY_CONTAIN }
}

/** An ingredient that brings an allergen, and how. */
data class AllergenCause(val ingredient: String, val level: AllergenLevel, val optional: Boolean)

/**
 * The allergens of a recipe: each one at the worst level of its ingredients, with the ingredients
 * that bring it.
 */
class RecipeAllergens(
    val causes: Map<Allergen, List<AllergenCause>>,
    /** Ingredients without the allergen that may carry traces of it, to buy certified. */
    val traceCauses: Map<Allergen, List<String>> = emptyMap()
) {
    fun level(allergen: Allergen): AllergenLevel =
        causes[allergen]?.maxOfOrNull { it.level } ?: AllergenLevel.FREE

    /** Allergens that are present or may be: CONTAINS first, each group in the EU order. */
    val present: List<Allergen>
        get() = Allergen.entries.filter { level(it) != AllergenLevel.FREE }
            .sortedBy { if (level(it) == AllergenLevel.CONTAINS) 0 else 1 }
}

/**
 * Works out allergens from ingredient names with [AllergenRules], with no AI: instant, free and
 * the same every time. It cannot see brands or labels, so whatever depends on the product is
 * MAY_CONTAIN; a name that says "sin gluten" or "sin lactosa" (certified products) clears those.
 */
object AllergenAnalysis {

    private class Token(val text: String)
    private class Match(val first: Int, val count: Int, val rule: AllergenRule)

    private val WORD = Regex("[a-z0-9]+")
    private val GLUTEN_FREE = listOf(listOf("sin", "gluten"), listOf("gluten", "free"), listOf("glutenfree"), listOf("sin", "tacc"))
    private val LACTOSE_FREE = listOf(listOf("sin", "lactosa"), listOf("lactose", "free"), listOf("deslactosada"), listOf("deslactosado"))
    private val EGG_FREE = listOf(listOf("sin", "huevo"), listOf("egg", "free"))

    /** Filtering a library analyses the same names again and again; the rules never change. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, IngredientAllergens>()
    private const val CACHE_LIMIT = 5000

    fun ofIngredient(name: String): IngredientAllergens {
        cache[name]?.let { return it }
        if (cache.size > CACHE_LIMIT) cache.clear()
        return analyse(name).also { cache[name] = it }
    }

    private fun analyse(name: String): IngredientAllergens {
        val key = searchKey(name)
        val tokens = WORD.findAll(key).map { Token(it.value) }.toList()
        val matches = mutableListOf<Match>()
        for (rule in AllergenRules.all) for (pattern in rule.patterns) {
            for (i in 0..tokens.size - pattern.size) {
                if (pattern.indices.all { k -> sameWord(tokens[i + k].text, pattern[k]) }) matches += Match(i, pattern.size, rule)
            }
        }
        // The longest names first: "nuez moscada" before "nuez", "leche de coco" before "leche".
        val used = BooleanArray(tokens.size)
        val levels = mutableMapOf<Allergen, AllergenLevel>()
        val traces = mutableSetOf<Allergen>()
        for (match in matches.sortedWith(compareByDescending<Match> { it.count }.thenBy { it.first })) {
            val range = match.first until match.first + match.count
            if (range.any { used[it] }) continue
            range.forEach { used[it] = true }
            match.rule.effects.forEach { (allergen, level) -> levels.merge(allergen, level) { a, b -> maxOf(a, b) } }
            traces += match.rule.traces
        }
        fun says(phrases: List<List<String>>) = phrases.any { phrase ->
            (0..tokens.size - phrase.size).any { i -> phrase.indices.all { k -> tokens[i + k].text == phrase[k] } }
        }
        if (says(GLUTEN_FREE)) {
            levels.remove(Allergen.GLUTEN)
            traces.remove(Allergen.GLUTEN)
        }
        if (says(LACTOSE_FREE)) levels.remove(Allergen.LACTOSE)
        if (says(EGG_FREE)) levels.remove(Allergen.EGGS)
        return IngredientAllergens(levels, traces - levels.keys)
    }

    fun ofRecipe(recipe: Recipe): RecipeAllergens = ofIngredients(recipe.ingredientGroups.flatMap { it.ingredients })

    fun ofIngredients(ingredients: List<Ingredient>): RecipeAllergens {
        val causes = mutableMapOf<Allergen, MutableList<AllergenCause>>()
        val traceCauses = mutableMapOf<Allergen, MutableList<String>>()
        ingredients.filter { it.name.isNotBlank() }.forEach { ingredient ->
            val found = ofIngredient(ingredient.name)
            found.traces.forEach { allergen -> traceCauses.getOrPut(allergen) { mutableListOf() } += ingredient.name.trim() }
            found.levels.forEach { (allergen, level) ->
                val list = causes.getOrPut(allergen) { mutableListOf() }
                if (list.none { it.ingredient.equals(ingredient.name.trim(), ignoreCase = true) }) {
                    list += AllergenCause(ingredient.name.trim(), level, ingredient.optional)
                }
            }
        }
        return RecipeAllergens(
            causes.mapValues { (_, list) -> list.sortedByDescending { it.level } },
            traceCauses.mapValues { (_, list) -> list.distinctBy { it.lowercase() } }
        )
    }

    /** "huevo" matches "huevos", "nuez" matches "nueces", "pan" does not match "panceta". */
    private fun sameWord(token: String, word: String): Boolean =
        token == word || token == word + "s" || token == word + "es" ||
            (word.endsWith("z") && token == word.dropLast(1) + "ces")
}
