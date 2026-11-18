package org.calamares.miga.data.carbs

import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.searchKey

/** How an ingredient counts in the carbohydrates of a recipe. */
enum class CarbStatus {
    /** Its amount and food are known: [IngredientCarbs.grams] of carbohydrate. */
    COUNTED,

    /**
     * Next to nothing: under [FREE_LIMIT] grams, or, without an amount, a food that hardly has
     * any (meat, oil, spices...).
     */
    FREE,

    /** A food with carbohydrates but no amount to work them out ("flour to coat"). */
    NO_AMOUNT,

    /** A food the table does not know. */
    UNKNOWN
}

/** What one ingredient brings, for the whole recipe as written (not per serving). */
data class IngredientCarbs(
    val name: String,
    val status: CarbStatus,
    /** Grams of carbohydrate, when COUNTED (FREE ones may have a little, under [FREE_LIMIT]). */
    val grams: Double = 0.0,
    val optional: Boolean = false
)

/** The carbohydrates of a recipe as written, worked out from its ingredients. */
class RecipeCarbs(val items: List<IngredientCarbs>, val servings: Int?) {
    /** Grams of carbohydrate of the ingredients that count, optional ones left out. */
    val total: Double get() = items.filter { !it.optional }.sumOf { it.grams }

    /** Per serving when the recipe says how many; otherwise null. */
    val perServing: Double? get() = servings?.takeIf { it > 0 }?.let { total / it }

    /** Ingredients that could not be counted (unknown food or amount), optional ones left out. */
    val missing: List<IngredientCarbs> get() = items.filter { !it.optional && (it.status == CarbStatus.NO_AMOUNT || it.status == CarbStatus.UNKNOWN) }

    /** At least one ingredient, and every one that matters known. */
    val complete: Boolean get() = items.isNotEmpty() && missing.isEmpty()
}

/** Under this many grams of carbohydrate an ingredient is shown as "next to nothing". */
const val FREE_LIMIT = 1.0

/**
 * Works out carbohydrates from ingredient names and amounts with [CarbFoods], with no AI: the
 * same answer every time, and offline. Available carbohydrates (without fibre), the figure
 * carbohydrate counting uses; the food values are rounded from public tables and checked against
 * BEDCA (see CarbFoods).
 */
object CarbAnalysis {

    private class Match(val first: Int, val count: Int, val food: CarbFood)

    private val WORD = Regex("[a-z0-9]+")
    private val BIG = setOf("grande", "grandes", "large", "big")
    private val SMALL = setOf("pequena", "pequenas", "pequeno", "pequenos", "small")

    /** The food an ingredient name refers to: the longest name that matches, the first one on a tie. */
    fun foodOf(name: String): CarbFood? {
        val tokens = WORD.findAll(searchKey(name)).map { it.value }.toList()
        var best: Match? = null
        for (food in CarbFoods.all) for (pattern in food.patterns) {
            for (i in 0..tokens.size - pattern.size) {
                if (pattern.indices.all { k -> sameWord(tokens[i + k], pattern[k]) }) {
                    val current = best
                    if (current == null || pattern.size > current.count || (pattern.size == current.count && i < current.first)) {
                        best = Match(i, pattern.size, food)
                    }
                }
            }
        }
        return best?.food
    }

    fun ofIngredient(ingredient: Ingredient): IngredientCarbs {
        val name = ingredient.name.trim()
        val food = foodOf(name) ?: return IngredientCarbs(name, CarbStatus.UNKNOWN, optional = ingredient.optional)
        val grams = gramsOf(ingredient.quantity, ingredient.unit, food, name)
        val carbs = grams?.let { it * food.carbsPer100 / 100.0 }
        val status = when {
            carbs == null -> if (food.free) CarbStatus.FREE else CarbStatus.NO_AMOUNT
            carbs < FREE_LIMIT -> CarbStatus.FREE
            else -> CarbStatus.COUNTED
        }
        return IngredientCarbs(name, status, if (status == CarbStatus.NO_AMOUNT) 0.0 else carbs ?: 0.0, ingredient.optional)
    }

    fun ofRecipe(recipe: Recipe): RecipeCarbs =
        RecipeCarbs(
            recipe.ingredientGroups.flatMap { it.ingredients }.filter { it.name.isNotBlank() }.map { ofIngredient(it) },
            recipe.servings
        )

    /**
     * Grams of [food] in an amount, or null when it cannot be told: no quantity, or a unit with no
     * weight of its own and a food with no usual piece size.
     */
    internal fun gramsOf(quantity: Double?, unit: String?, food: CarbFood, name: String): Double? {
        val amount = quantity ?: return null
        val words = WORD.findAll(searchKey(unit.orEmpty())).map { it.value }.toList()
        val nameWords = WORD.findAll(searchKey(name)).map { it.value }.toSet()
        val size = when {
            (words + nameWords).any { it in BIG } -> 1.3
            (words + nameWords).any { it in SMALL } -> 0.7
            else -> 1.0
        }
        val unitWord = words.firstOrNull { it !in BIG && it !in SMALL }
        if (unitWord == null) return (food.gramsPerPiece ?: Units.pieceFromName(nameWords))?.let { amount * it * size }
        Units.massGrams(unitWord)?.let { return amount * it }
        Units.volumeMl(unitWord)?.let { return amount * it * size * food.gramsPerMl }
        Units.pieceGrams(unitWord, food)?.let { return amount * it * size }
        return null
    }

    /** "huevo" matches "huevos", "nuez" matches "nueces", "pan" does not match "panceta". */
    private fun sameWord(token: String, word: String): Boolean =
        token == word || token == word + "s" || token == word + "es" ||
            (word.endsWith("z") && token == word.dropLast(1) + "ces")
}

/** Weights of the units cooks write, in Spanish and English (search keys, singular or plural). */
internal object Units {

    private fun key(word: String): String = when {
        word.length > 3 && word.endsWith("es") && word.dropLast(2) in KNOWN -> word.dropLast(2)
        word.length > 2 && word.endsWith("s") && word.dropLast(1) in KNOWN -> word.dropLast(1)
        else -> word
    }

    private val MASS = mapOf(
        "g" to 1.0, "gr" to 1.0, "grs" to 1.0, "gramo" to 1.0, "gram" to 1.0, "kg" to 1000.0, "kilo" to 1000.0,
        "kilogramo" to 1000.0, "kilogram" to 1000.0, "mg" to 0.001, "oz" to 28.35, "ounce" to 28.35,
        "lb" to 453.6, "lbs" to 453.6, "libra" to 453.6, "pound" to 453.6
    )

    private val VOLUME = mapOf(
        "ml" to 1.0, "mililitro" to 1.0, "milliliter" to 1.0, "millilitre" to 1.0, "cl" to 10.0, "centilitro" to 10.0,
        "dl" to 100.0, "l" to 1000.0, "litro" to 1000.0, "liter" to 1000.0, "litre" to 1000.0,
        "taza" to 240.0, "cup" to 240.0, "vaso" to 200.0, "glass" to 200.0, "vasito" to 100.0, "copa" to 150.0,
        "cucharada" to 15.0, "cda" to 15.0, "tbsp" to 15.0, "tbs" to 15.0, "tablespoon" to 15.0, "cucharon" to 100.0,
        "cucharadita" to 5.0, "cdta" to 5.0, "cdita" to 5.0, "tsp" to 5.0, "teaspoon" to 5.0, "cazo" to 100.0,
        "cacito" to 50.0, "chupito" to 40.0, "chorro" to 15.0, "chorrito" to 10.0, "splash" to 10.0, "dash" to 2.0,
        "gota" to 0.05, "drop" to 0.05, "pint" to 473.0, "quart" to 946.0
    )

    private val KNOWN = MASS.keys + VOLUME.keys + setOf(
        "pizca", "pinch", "pellizco", "punado", "handful", "diente", "clove", "hoja", "leaf", "rama", "ramita",
        "sprig", "manojo", "bunch", "rodaja", "rebanada", "slice", "loncha", "lamina", "sheet", "lata", "can", "tin",
        "sobre", "sobrecito", "sachet", "envelope", "cubito", "cube", "pastilla", "onza", "tableta", "terron", "cm",
        "unidad", "unit", "pieza", "piece", "trozo", "tallo", "stalk", "cabeza", "head", "docena", "bola", "scoop",
        "gajo", "wedge", "barra", "loaf", "filete", "fillet", "vaina", "pod", "knob", "nuez", "hebra", "stick", "tira",
        "strip", "racion", "porcion", "portion"
    )

    /**
     * Usual weight of a piece named in the ingredient, for foods without one of their own (meat
     * and fish come in many cuts): "4 lomos de bacalao", "2 pechugas de pollo".
     */
    private val PIECES_BY_NAME = mapOf(
        "lomo" to 150.0, "filete" to 150.0, "rodaja" to 150.0, "supremas" to 150.0, "suprema" to 150.0, "fillet" to 150.0,
        "steak" to 200.0, "loin" to 150.0, "pechuga" to 200.0, "breast" to 200.0, "muslo" to 120.0, "thigh" to 100.0,
        "contramuslo" to 100.0, "drumstick" to 100.0, "ala" to 60.0, "alita" to 50.0, "wing" to 60.0, "chuleta" to 150.0,
        "chop" to 150.0, "chuleton" to 500.0, "entrecot" to 250.0, "hamburguesa" to 120.0, "burger" to 120.0,
        "salchicha" to 60.0, "sausage" to 60.0, "codorniz" to 150.0, "quail" to 150.0, "picanton" to 400.0,
        "dorada" to 400.0, "lubina" to 400.0, "trucha" to 300.0, "caballa" to 300.0, "sardina" to 50.0, "sardine" to 50.0,
        "boqueron" to 20.0, "salmonete" to 120.0, "gamba" to 15.0, "langostino" to 25.0, "prawn" to 15.0, "cigala" to 60.0,
        "mejillon" to 15.0, "mussel" to 15.0, "almeja" to 10.0, "clam" to 10.0, "vieira" to 40.0, "scallop" to 40.0,
        "calamar" to 200.0, "sepia" to 300.0, "pulpo" to 1500.0, "chorizo" to 100.0, "morcilla" to 150.0,
        "butifarra" to 150.0, "solomillo" to 450.0, "magret" to 350.0, "lenguado" to 300.0, "bogavante" to 600.0,
        "conejo" to 1200.0, "gallina" to 1800.0, "paletilla" to 900.0, "codillo" to 900.0, "presa" to 150.0,
        "mozzarella" to 125.0, "lengua" to 1000.0, "manitas" to 300.0, "porciones" to 15.0
    )

    fun pieceFromName(words: Set<String>): Double? =
        words.firstNotNullOfOrNull { word -> PIECES_BY_NAME[word] ?: PIECES_BY_NAME[word.removeSuffix("es")] ?: PIECES_BY_NAME[word.removeSuffix("s")] }

    fun massGrams(word: String): Double? = MASS[key(word)]

    fun volumeMl(word: String): Double? = VOLUME[key(word)]

    /** Grams for units that name a piece or a small amount, depending on the food when it matters. */
    fun pieceGrams(word: String, food: CarbFood): Double? = when (key(word)) {
        "pizca", "pinch", "pellizco", "hebra" -> 0.5
        "punado", "handful" -> 30.0
        "diente", "clove" -> 5.0
        "hoja", "leaf", "vaina", "pod" -> 1.0
        "rama", "ramita", "sprig" -> 3.0
        "manojo", "bunch" -> 40.0
        "rodaja", "gajo", "wedge" -> 15.0
        "rebanada", "slice" -> food.gramsPerPiece?.takeIf { food.sliced } ?: 25.0
        "loncha", "tira", "strip" -> 20.0
        "lamina", "sheet" -> 20.0
        "lata", "can", "tin" -> 300.0
        "sobre", "sobrecito", "sachet", "envelope" -> 10.0
        "cubito", "cube", "pastilla" -> 10.0
        "onza" -> 10.0
        "tableta" -> 100.0
        "terron" -> 5.0
        "cm" -> 4.0
        "nuez", "knob" -> 15.0
        "tallo", "stalk", "stick" -> 40.0
        "cabeza", "head" -> 50.0
        "bola", "scoop" -> 60.0
        "barra", "loaf" -> 250.0
        "filete", "fillet" -> 150.0
        "docena" -> food.gramsPerPiece?.let { it * 12 }
        "unidad", "unit", "pieza", "piece", "trozo", "racion", "porcion", "portion" -> food.gramsPerPiece
        else -> null
    }
}
