package org.calamares.miga.data.tips

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.searchKey
import java.time.LocalDate

/** Groups of cooking tips, in the order the tips screen shows them. */
enum class TipCategory(@StringRes private val labelRes: Int, val emoji: String) {
    TECHNIQUES(R.string.tip_category_techniques, "🔪"),
    PRESERVING(R.string.tip_category_preserving, "❄️"),
    LEFTOVERS(R.string.tip_category_leftovers, "♻️"),
    SUBSTITUTIONS(R.string.tip_category_substitutions, "🔄"),
    SAFETY(R.string.tip_category_safety, "🧼"),
    BAKING(R.string.tip_category_baking, "🧁"),
    TIME_SAVING(R.string.tip_category_time_saving, "⏱️"),
    KITCHEN(R.string.tip_category_kitchen, "🍳");

    val label: String get() = L10n.str(labelRes)
}

/**
 * A short cooking tip in the app language. [keywords] holds the words of both languages (without
 * accents), so a Spanish recipe still finds its tips when the app is in English and the other
 * way round.
 */
data class CookingTip(
    val id: String,
    val category: TipCategory,
    val title: String,
    val body: String,
    val keywords: List<String>
)

@Serializable
internal data class TipTextDto(val title: String, val body: String, val keywords: List<String> = emptyList())

@Serializable
internal data class TipDto(val id: String, val category: String, val es: TipTextDto, val en: TipTextDto)

@Serializable
internal data class TipsFileDto(val tips: List<TipDto>)

/** The tips bundled with the app (assets/tips/tips.json, Spanish and English side by side). */
object CookingTips {
    private const val ASSET = "tips/tips.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: Pair<String, List<CookingTip>>? = null

    /** Every tip in [language] ("es" or "en"), in file order. Read once and kept in memory. */
    fun all(context: Context, language: String = appLanguage()): List<CookingTip> {
        cached?.takeIf { it.first == language }?.let { return it.second }
        val text = context.applicationContext.assets.open(ASSET).bufferedReader().use { it.readText() }
        return parse(text, language).also { cached = language to it }
    }

    internal fun parse(text: String, language: String): List<CookingTip> =
        json.decodeFromString(TipsFileDto.serializer(), text).tips.mapNotNull { dto ->
            val category = runCatching { TipCategory.valueOf(dto.category) }.getOrNull() ?: return@mapNotNull null
            val shown = if (language == "es") dto.es else dto.en
            CookingTip(
                id = dto.id,
                category = category,
                title = shown.title,
                body = shown.body,
                keywords = (dto.es.keywords + dto.en.keywords).map(::searchKey).distinct()
            )
        }

    private fun appLanguage(): String = if (L10n.locale().language == "es") "es" else "en"
}

/**
 * The tip shown on [date]. Every tip comes up once before any repeats, in an order mixed so that
 * days in a row don't fall in the same category.
 */
fun tipOfTheDay(tips: List<CookingTip>, date: LocalDate = LocalDate.now()): CookingTip? {
    if (tips.isEmpty()) return null
    val order = tips.sortedBy { it.id }.shuffled(kotlin.random.Random(TIP_ORDER_SEED))
    return order[Math.floorMod(date.toEpochDay(), order.size.toLong()).toInt()]
}

private const val TIP_ORDER_SEED = 20261114

/** Tips whose title or text contain [query] (accents and case ignored). */
fun searchTips(tips: List<CookingTip>, query: String): List<CookingTip> {
    val key = searchKey(query.trim())
    if (key.isEmpty()) return tips
    return tips.filter { searchKey(it.title).contains(key) || searchKey(it.body).contains(key) || it.keywords.any { k -> k.contains(key) } }
}

/**
 * The tips that help with [recipe], best first: those whose keywords appear in its name,
 * ingredients or equipment weigh more than those only mentioned in a step or a tag. A keyword
 * matches whole words, also in plural ("huevo" finds "huevos", "tomato" finds "tomatoes").
 */
fun tipsForRecipe(recipe: Recipe, tips: List<CookingTip>, limit: Int = MAX_RECIPE_TIPS): List<CookingTip> {
    val fields = listOf(
        TITLE_WEIGHT to listOf(recipe.name),
        INGREDIENT_WEIGHT to recipe.ingredientGroups.flatMap { group -> group.ingredients.map { it.name } },
        INGREDIENT_WEIGHT to recipe.utensils,
        STEP_WEIGHT to recipe.stepGroups.flatMap { it.instructions },
        STEP_WEIGHT to recipe.tags
    ).map { (weight, texts) -> weight to texts.map(::words) }
    return tips.asSequence()
        .filter { it.keywords.isNotEmpty() }
        .map { tip -> tip to score(tip, fields) }
        .filter { (_, score) -> score > 0 }
        .sortedByDescending { (_, score) -> score }
        .take(limit)
        .map { (tip, _) -> tip }
        .toList()
}

private const val MAX_RECIPE_TIPS = 3
private const val TITLE_WEIGHT = 3
private const val INGREDIENT_WEIGHT = 2
private const val STEP_WEIGHT = 1

private fun score(tip: CookingTip, fields: List<Pair<Int, List<List<String>>>>): Int =
    tip.keywords.sumOf { keyword ->
        val parts = words(keyword)
        // Each keyword counts once, by the strongest place it appears in.
        fields.filter { (_, texts) -> texts.any { it.containsPhrase(parts) } }.maxOfOrNull { it.first } ?: 0
    }

private fun words(text: String): List<String> = searchKey(text).split(NON_WORD).filter { it.isNotEmpty() }

private val NON_WORD = Regex("[^\\p{L}\\d]+")

private fun List<String>.containsPhrase(phrase: List<String>): Boolean {
    if (phrase.isEmpty() || phrase.size > size) return false
    return (0..size - phrase.size).any { start -> phrase.indices.all { i -> sameWord(this[start + i], phrase[i]) } }
}

/** [word] is [keyword] or its plural (-s, -es). */
private fun sameWord(word: String, keyword: String): Boolean =
    word == keyword || word == keyword + "s" || word == keyword + "es"
