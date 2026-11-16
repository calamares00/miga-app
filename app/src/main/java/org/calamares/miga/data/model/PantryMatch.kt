package org.calamares.miga.data.model

/** A recipe that can be made with what the user has: what it uses of it and what is missing. */
data class PantryMatch(val recipe: Recipe, val used: Int, val missing: List<String>)

/** Basics every kitchen has; never counted as missing. */
private val STAPLES = setOf("sal", "agua", "aceite", "aceite de oliva", "pimienta", "salt", "water", "oil", "olive oil", "pepper")

/**
 * Recipes that use at least one of [have], best first: fewest missing ingredients, then most of
 * [have] used. Optional ingredients and kitchen basics (salt, water, oil, pepper) never count as
 * missing. Matching ignores accents, case and simple plurals ("tomate" finds "tomates triturados").
 */
fun recipesWithWhatYouHave(recipes: List<Recipe>, have: Collection<String>): List<PantryMatch> {
    val wanted = have.map { searchKey(it.trim()) }.filter { it.isNotEmpty() }.distinct()
    if (wanted.isEmpty()) return emptyList()
    return recipes.mapNotNull { recipe ->
        val ingredients = recipe.ingredientGroups.flatMap { it.ingredients }.filter { it.name.isNotBlank() }
        var used = 0
        val missing = mutableListOf<String>()
        ingredients.forEach { ingredient ->
            val key = searchKey(ingredient.name.trim())
            when {
                wanted.any { ingredientMatches(key, it) } -> used++
                ingredient.optional || key in STAPLES -> Unit
                else -> missing += ingredient.name.trim()
            }
        }
        if (used == 0) null else PantryMatch(recipe, used, missing.distinct())
    }.sortedWith(compareBy<PantryMatch> { it.missing.size }.thenByDescending { it.used }.thenBy { it.recipe.name.lowercase() })
}

/** Whether an ingredient ([ingredient], already a search key) is what the user has ([have]). */
private fun ingredientMatches(ingredient: String, have: String): Boolean {
    if (have.contains(' ')) return ingredient.contains(have)
    return ingredient.split(' ', ',', '(', ')').any { word -> word == have || samePlural(word, have) }
}

private fun samePlural(a: String, b: String): Boolean {
    val (short, long) = if (a.length <= b.length) a to b else b to a
    return short.length >= 3 && (long == short + "s" || long == short + "es")
}
