package org.calamares.miga.data.carbs

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.searchKey

/**
 * The six groups of the Spanish portion plans for diabetes ("planes de alimentación por
 * raciones", Clínic method: Jansà and Vidal, Actividad Dietética 22, 2004). One portion of a group
 * has 10 g of its main nutrient; [carbs], [protein], [fat] and [kcal] are the average portion the
 * method uses for every food of the group. Constant names may be stored.
 */
enum class FoodGroup(private val labelRes: Int, val carbs: Double, val protein: Double, val fat: Double, val kcal: Double) {
    MILK(R.string.group_milk, 10.0, 6.0, 6.0, 120.0),
    PROTEIN(R.string.group_protein, 0.0, 10.0, 5.0, 85.0),
    VEGETABLES(R.string.group_vegetables, 10.0, 0.0, 0.0, 40.0),
    STARCHES(R.string.group_starches, 10.0, 1.5, 0.0, 46.0),
    FRUIT(R.string.group_fruit, 10.0, 0.0, 0.0, 40.0),
    FAT(R.string.group_fat, 0.0, 0.0, 10.0, 90.0);

    val label: String get() = L10n.str(labelRes)
}

/**
 * How a food counts in the groups: by its carbohydrate (10 g = 1 portion of [group]) or, for
 * protein and fat foods, by the weight of one portion in the method's tables.
 */
private sealed interface Counts {
    data class ByCarbs(val group: FoodGroup) : Counts
    data class ByWeight(val group: FoodGroup, val gramsPerPortion: Double) : Counts
}

/** Portions of each group, and the carbohydrate that falls outside the groups (sugar, sweets...). */
data class GroupPortions(
    val portions: Map<FoodGroup, Double> = emptyMap(),
    val otherCarbs: Double = 0.0,
    /** Oil to fry or confit with: only [MAX_OIL_PER_SERVING] portions per serving are counted. */
    val cookingOil: Boolean = false
) {
    operator fun get(group: FoodGroup): Double = portions[group] ?: 0.0

    operator fun plus(other: GroupPortions) = GroupPortions(
        FoodGroup.entries.associateWith { this[it] + other[it] }.filterValues { it > 0.0 },
        otherCarbs + other.otherCarbs,
        cookingOil || other.cookingOil
    )

    fun times(factor: Double) = GroupPortions(portions.mapValues { it.value * factor }, otherCarbs * factor, cookingOil)

    /** Approximate energy, from the method's average portions (sugar and the like at 4 kcal/g). */
    val kcal: Double get() = FoodGroup.entries.sumOf { this[it] * it.kcal } + otherCarbs * 4

    val isEmpty: Boolean get() = portions.values.all { it <= 0.0 } && otherCarbs <= 0.0
}

/**
 * Which group each food of [CarbFoods] belongs to. Weights per portion from the Clínic method's
 * tables: 50 g of meat, 75 g of fish, 40 g of cheese, 1 egg, 1 tablespoon of oil (about 14 g), 10 g of
 * butter, 40 g of olives, 30 g of cream, 15 g of nuts. Foods the method leaves out of the plans
 * (sugar, sweets, sauces...) count only as "other carbohydrate".
 */
object FoodGroups {
    private val STARCH_FOODS = setOf(
        "rice", "brown_rice", "cooked_rice", "oats", "couscous", "bulgur", "quinoa", "wheat_flour", "wholewheat_flour",
        "corn_flour", "rice_flour", "chickpea_flour", "starch", "semolina", "pasta", "gnocchi", "rice_noodles", "cooked_pasta",
        "bread", "sliced_bread", "wholemeal_bread", "toast", "breadcrumbs", "tortilla_wheat", "tortilla_corn",
        "puff_pastry", "shortcrust", "pizza_dough", "biscuits", "cornflakes", "chickpeas", "chickpeas_cooked", "lentils",
        "lentils_cooked", "beans", "beans_cooked", "peas", "split_peas", "broad_beans", "soy_beans", "cassava", "potato",
        "sweet_potato", "fries", "crisps", "mashed_flakes", "corn", "chestnuts", "blood_sausage_rice"
    )
    private val VEGETABLE_FOODS = setOf(
        "edamame", "turnip", "onion", "spring_onion", "leek", "garlic", "carrot", "beetroot", "parsnip", "pumpkin", "tomato",
        "crushed_tomato", "tomato_paste", "tomato_sauce", "pepper", "green_beans", "artichoke", "celeriac", "leafy",
        "cabbage", "crucifer", "courgette", "mushrooms"
    )
    private val FRUIT_FOODS = setOf(
        "apple", "pear", "banana", "orange", "lemon", "citrus_juice", "orange_juice", "fruit_juice", "peach", "berries",
        "cherries", "grapes", "melon", "pineapple", "pineapple_syrup", "kiwi", "mango", "dried_fruit"
    )
    private val MILK_FOODS = setOf("milk", "plant_milk", "evaporated_milk", "yogurt", "bechamel")
    private val BY_WEIGHT = mapOf(
        "meat" to (FoodGroup.PROTEIN to 50.0), "fish" to (FoodGroup.PROTEIN to 75.0), "surimi" to (FoodGroup.PROTEIN to 75.0),
        "egg" to (FoodGroup.PROTEIN to 55.0), "cheese" to (FoodGroup.PROTEIN to 40.0), "tofu" to (FoodGroup.PROTEIN to 80.0),
        "oil" to (FoodGroup.FAT to 14.0), "butter" to (FoodGroup.FAT to 10.0), "olives" to (FoodGroup.FAT to 40.0),
        "cream" to (FoodGroup.FAT to 30.0), "nuts" to (FoodGroup.FAT to 15.0), "cashews" to (FoodGroup.FAT to 15.0),
        "peanuts" to (FoodGroup.FAT to 15.0), "seeds" to (FoodGroup.FAT to 15.0), "coconut" to (FoodGroup.FAT to 15.0),
        "avocado" to (FoodGroup.FAT to 65.0), "coconut_milk" to (FoodGroup.FAT to 50.0)
    )

    private fun countsOf(food: CarbFood): List<Counts> = buildList {
        BY_WEIGHT[food.id]?.let { (group, grams) -> add(Counts.ByWeight(group, grams)) }
        when (food.id) {
            in STARCH_FOODS -> add(Counts.ByCarbs(FoodGroup.STARCHES))
            in VEGETABLE_FOODS -> add(Counts.ByCarbs(FoodGroup.VEGETABLES))
            in FRUIT_FOODS -> add(Counts.ByCarbs(FoodGroup.FRUIT))
            in MILK_FOODS -> add(Counts.ByCarbs(FoodGroup.MILK))
        }
        // Blood sausage with rice is also meat.
        if (food.id == "blood_sausage_rice") add(Counts.ByWeight(FoodGroup.PROTEIN, 40.0))
    }

    private val WORD = Regex("[a-z0-9]+")

    /** Bones and shells for stock: they flavour the dish but are not eaten. */
    private val NOT_EATEN = setOf("hueso", "huesos", "carcasa", "carcasas", "espinas", "cabezas", "bone", "bones", "carcass", "carcasses", "tuetano")
    private val BONELESS = setOf("deshuesado", "deshuesados", "deshuesada", "deshuesadas", "fileteado", "fileteada", "lomo", "lomos", "filete", "filetes", "pelado", "pelados", "peladas", "pelada", "carne", "boneless", "peeled", "fillet", "fillets", "sin")
    private val IN_SHELL = setOf("mejillon", "mejillones", "almeja", "almejas", "berberecho", "berberechos", "navaja", "navajas", "chirla", "chirlas", "ostra", "ostras", "zamburina", "zamburinas", "caracol", "caracoles", "mussels", "clams", "cockles", "oysters", "snails")
    private val WHOLE_SHELLFISH = setOf("gamba", "gambas", "langostino", "langostinos", "cigala", "cigalas", "carabinero", "carabineros", "bogavante", "langosta", "centollo", "necora", "necoras", "cangrejo", "cangrejos", "prawns", "shrimp", "langoustines", "lobster", "crab")
    private val ON_THE_BONE = setOf("chuleta", "chuletas", "chuletilla", "chuletillas", "costilla", "costillas", "costillar", "rack", "alita", "alitas", "ala", "alas", "muslo", "muslos", "contramuslo", "contramuslos", "troceado", "troceada", "entero", "entera", "conejo", "codorniz", "codornices", "perdiz", "perdices", "rabo", "osobuco", "jarrete", "codillo", "codillos", "manitas", "paletilla", "paletillas", "pierna", "cochinillo", "picantones", "chuleton", "gallina", "ribs", "chops", "wings", "drumsticks", "thighs", "shank", "oxtail")
    private val WHOLE_FISH = setOf("dorada", "doradas", "lubina", "lubinas", "besugo", "trucha", "truchas", "lenguado", "lenguados", "rodaballo", "sargo", "salmonete", "salmonetes", "caballa", "caballas", "sardinas", "boquerones")

    /**
     * The part of a meat or fish that is eaten, from its name: the method weighs food "raw and
     * clean", but recipes weigh mussels in their shells and chops on the bone.
     */
    internal fun edibleShare(name: String): Double {
        val words = WORD.findAll(searchKey(name)).map { it.value }.toSet()
        val boneless = words.any { it in BONELESS }
        return when {
            words.any { it in NOT_EATEN } -> 0.0
            boneless -> 1.0
            words.any { it in IN_SHELL } -> 0.3
            words.any { it in WHOLE_SHELLFISH } -> 0.45
            words.any { it in WHOLE_FISH } -> 0.5
            words.any { it in ON_THE_BONE } -> 0.65
            else -> 1.0
        }
    }

    /** The portions of one ingredient, for the whole recipe; empty when its amount is unknown. */
    fun ofIngredient(ingredient: Ingredient): GroupPortions {
        val food = CarbAnalysis.foodOf(ingredient.name) ?: return GroupPortions()
        val grams = CarbAnalysis.gramsOf(ingredient.quantity, ingredient.unit, food, ingredient.name) ?: return GroupPortions()
        val counts = countsOf(food)
        val portions = mutableMapOf<FoodGroup, Double>()
        counts.forEach { count ->
            when (count) {
                is Counts.ByCarbs -> portions.merge(count.group, grams * food.carbsPer100 / 100 / 10, Double::plus)
                is Counts.ByWeight -> {
                    val edible = if (count.group == FoodGroup.PROTEIN) edibleShare(ingredient.name) else 1.0
                    portions.merge(count.group, grams * edible / count.gramsPerPortion, Double::plus)
                }
            }
        }
        val carbsInGroups = counts.any { it is Counts.ByCarbs }
        return GroupPortions(portions, if (carbsInGroups) 0.0 else grams * food.carbsPer100 / 100)
    }

    /**
     * Per serving (or the whole recipe when it says no servings), optional ingredients left out.
     * Oil beyond [MAX_OIL_PER_SERVING] portions per serving is for frying or confit and mostly
     * stays in the pan, so it is not counted (and [GroupPortions.cookingOil] says so).
     */
    fun ofRecipe(recipe: Recipe): GroupPortions {
        val ingredients = recipe.ingredientGroups.flatMap { it.ingredients }.filter { it.name.isNotBlank() && !it.optional }
        val servings = recipe.servings.takeIf { it > 0 } ?: 1
        val oil = ingredients.filter { CarbAnalysis.foodOf(it.name)?.id == "oil" }
        val others = (ingredients - oil.toSet()).fold(GroupPortions()) { sum, ingredient -> sum + ofIngredient(ingredient) }
        val oilPortions = oil.sumOf { ofIngredient(it)[FoodGroup.FAT] } / servings
        val capped = oilPortions > MAX_OIL_PER_SERVING
        val oilPerServing = GroupPortions(mapOf(FoodGroup.FAT to minOf(oilPortions, MAX_OIL_PER_SERVING)).filterValues { it > 0.0 }, 0.0, capped)
        return others.times(1.0 / servings) + oilPerServing
    }

    /** Portions of oil (tablespoons) per serving a dish can really take; more is cooking oil. */
    const val MAX_OIL_PER_SERVING = 3.0
}
