package org.calamares.miga.data.stats

import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBook
import org.calamares.miga.data.model.RecipeOrigin
import java.util.Calendar

/** A recipe in a ranking, with the value it is ranked by (times cooked, stars...). */
data class RankedRecipe(val recipeId: Long, val name: String, val value: Int)

/** A label and how many recipes have it. */
data class CountEntry(val label: String, val count: Int)

/** Everything the statistics screen shows about the recipe library. */
data class LibraryStats(
    val totalRecipes: Int,
    val totalBooks: Int,
    val favorites: Int,
    val timesCookedTotal: Int,
    val photos: Int,
    val addedThisMonth: Int,
    /** Average prep + cook time of the recipes that have one; null when none has. */
    val averageMinutes: Int?,
    val quickRecipes: Int,
    val longestRecipe: RankedRecipe?,
    val averageRating: Double?,
    val ratedRecipes: Int,
    val mostCooked: List<RankedRecipe>,
    val topRated: List<RankedRecipe>,
    val byBook: List<CountEntry>,
    val byCategory: List<CountEntry>,
    val byDifficulty: List<CountEntry>,
    val topIngredients: List<CountEntry>,
    val topTags: List<CountEntry>,
    /** Recipes per country of origin, labelled with the flag and the country name. */
    val byOrigin: List<CountEntry>,
    val withoutPhoto: Int,
    val withoutCategory: Int,
    val withoutIngredients: Int,
    val withoutSteps: Int,
    val withoutTime: Int
) {
    val incomplete: Boolean
        get() = withoutPhoto + withoutCategory + withoutIngredients + withoutSteps + withoutTime > 0
}

object LibraryStatsCalculator {

    const val QUICK_RECIPE_MINUTES = 30
    private const val MAX_RANKED = 5
    private const val MAX_CATEGORIES = 8
    private const val MAX_BOOKS = 8
    private const val MAX_INGREDIENTS = 10
    private const val MAX_TAGS = 12

    /**
     * Computes the statistics in memory. [uncategorized] and [others] are the labels for recipes
     * without a category and for the categories beyond the ones listed.
     */
    fun compute(
        recipes: List<Recipe>,
        books: List<RecipeBook>,
        now: Long,
        uncategorized: String,
        others: String,
        difficultyLabel: (Difficulty) -> String = { it.label }
    ): LibraryStats {
        val timed = recipes.mapNotNull { recipe -> recipe.totalTimeMinutes?.takeIf { it > 0 }?.let { recipe to it } }
        val rated = recipes.filter { it.rating != null }
        return LibraryStats(
            totalRecipes = recipes.size,
            totalBooks = books.size,
            favorites = recipes.count { it.isFavorite },
            timesCookedTotal = recipes.sumOf { it.timesCooked },
            photos = recipes.sumOf { it.photos.size },
            addedThisMonth = recipes.count { sameMonth(it.createdAt, now) },
            averageMinutes = timed.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt(),
            quickRecipes = timed.count { it.second <= QUICK_RECIPE_MINUTES },
            longestRecipe = timed.maxByOrNull { it.second }?.let { (recipe, minutes) -> RankedRecipe(recipe.id, recipe.name, minutes) },
            averageRating = rated.takeIf { it.isNotEmpty() }?.map { it.rating!! }?.average(),
            ratedRecipes = rated.size,
            mostCooked = recipes.filter { it.timesCooked > 0 }
                .sortedWith(compareByDescending<Recipe> { it.timesCooked }.thenBy { it.name.lowercase() })
                .take(MAX_RANKED)
                .map { RankedRecipe(it.id, it.name, it.timesCooked) },
            topRated = rated
                .sortedWith(compareByDescending<Recipe> { it.rating }.thenByDescending { it.timesCooked }.thenBy { it.name.lowercase() })
                .take(MAX_RANKED)
                .map { RankedRecipe(it.id, it.name, it.rating ?: 0) },
            byBook = recipes.groupingBy { it.recipeBookName }.eachCount()
                .map { (name, count) -> CountEntry(name, count) }
                .sortedByDescending { it.count }
                .take(MAX_BOOKS),
            byCategory = topWithOthers(
                recipes.groupingBy { it.categoryName?.takeIf { name -> name.isNotBlank() } ?: uncategorized }.eachCount(),
                MAX_CATEGORIES,
                others
            ),
            byDifficulty = Difficulty.entries
                .map { difficulty -> CountEntry(difficultyLabel(difficulty), recipes.count { it.difficulty == difficulty }) }
                .filter { it.count > 0 },
            topIngredients = mostCommon(recipes.map { recipe -> recipe.ingredientGroups.flatMap { group -> group.ingredients.map { it.name } } }, MAX_INGREDIENTS),
            topTags = mostCommon(recipes.map { it.tags }, MAX_TAGS),
            byOrigin = recipes.mapNotNull { it.originCountry }.groupingBy { it }.eachCount()
                .map { (code, count) -> CountEntry(RecipeOrigin.label(null, code).orEmpty(), count) }
                .sortedByDescending { it.count }
                .take(MAX_CATEGORIES),
            withoutPhoto = recipes.count { it.photos.isEmpty() },
            withoutCategory = recipes.count { it.categoryName.isNullOrBlank() },
            withoutIngredients = recipes.count { recipe -> recipe.ingredientGroups.all { it.ingredients.isEmpty() } },
            withoutSteps = recipes.count { recipe -> recipe.stepGroups.all { it.instructions.isEmpty() } },
            withoutTime = recipes.count { it.totalTimeMinutes == null || it.totalTimeMinutes == 0 }
        )
    }

    /** The [max] largest counts, plus one entry with the rest added up under [others]. */
    private fun topWithOthers(counts: Map<String, Int>, max: Int, others: String): List<CountEntry> {
        val sorted = counts.map { (label, count) -> CountEntry(label, count) }.sortedByDescending { it.count }
        if (sorted.size <= max) return sorted
        return sorted.take(max - 1) + CountEntry(others, sorted.drop(max - 1).sumOf { it.count })
    }

    /**
     * The names that appear in most recipes ([namesPerRecipe] has one list per recipe), compared
     * without case or extra spaces. Each is shown as first written.
     */
    private fun mostCommon(namesPerRecipe: List<List<String>>, max: Int): List<CountEntry> {
        val counts = LinkedHashMap<String, Int>()
        val labels = HashMap<String, String>()
        namesPerRecipe.forEach { names ->
            names.mapNotNull { name ->
                val label = name.trim().replace(Regex("\\s+"), " ")
                label.takeIf { it.isNotEmpty() }?.let { it.lowercase() to it }
            }.distinctBy { it.first }.forEach { (key, label) ->
                counts[key] = (counts[key] ?: 0) + 1
                labels.putIfAbsent(key, label.replaceFirstChar { it.uppercase() })
            }
        }
        return counts.entries
            .filter { it.value > 1 || counts.size <= max }
            .sortedByDescending { it.value }
            .take(max)
            .map { CountEntry(labels.getValue(it.key), it.value) }
    }

    private fun sameMonth(epochMillis: Long, now: Long): Boolean {
        val entry = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val current = Calendar.getInstance().apply { timeInMillis = now }
        return entry.get(Calendar.YEAR) == current.get(Calendar.YEAR) && entry.get(Calendar.MONTH) == current.get(Calendar.MONTH)
    }
}
