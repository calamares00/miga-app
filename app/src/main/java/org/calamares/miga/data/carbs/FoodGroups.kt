package org.calamares.miga.data.carbs

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.Recipe

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
data class GroupPortions(val portions: Map<FoodGroup, Double> = emptyMap(), val otherCarbs: Double = 0.0) {
    operator fun get(group: FoodGroup): Double = portions[group] ?: 0.0

    operator fun plus(other: GroupPortions) = GroupPortions(
        FoodGroup.entries.associateWith { this[it] + other[it] }.filterValues { it > 0.0 },
        otherCarbs + other.otherCarbs
    )

    fun times(factor: Double) = GroupPortions(portions.mapValues { it.value * factor }, otherCarbs * factor)

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
        "corn_flour", "rice_flour", "chickpea_flour", "starch", "semolina", "pasta", "rice_noodles", "cooked_pasta",
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

    /** The portions of one ingredient, for the whole recipe; empty when its amount is unknown. */
    fun ofIngredient(ingredient: Ingredient): GroupPortions {
        val food = CarbAnalysis.foodOf(ingredient.name) ?: return GroupPortions()
        val grams = CarbAnalysis.gramsOf(ingredient.quantity, ingredient.unit, food, ingredient.name) ?: return GroupPortions()
        val counts = countsOf(food)
        val portions = mutableMapOf<FoodGroup, Double>()
        counts.forEach { count ->
            when (count) {
                is Counts.ByCarbs -> portions.merge(count.group, grams * food.carbsPer100 / 100 / 10, Double::plus)
                is Counts.ByWeight -> portions.merge(count.group, grams / count.gramsPerPortion, Double::plus)
            }
        }
        val carbsInGroups = counts.any { it is Counts.ByCarbs }
        return GroupPortions(portions, if (carbsInGroups) 0.0 else grams * food.carbsPer100 / 100)
    }

    /** Per serving (or the whole recipe when it says no servings), optional ingredients left out. */
    fun ofRecipe(recipe: Recipe): GroupPortions {
        val total = recipe.ingredientGroups.flatMap { it.ingredients }
            .filter { it.name.isNotBlank() && !it.optional }
            .fold(GroupPortions()) { sum, ingredient -> sum + ofIngredient(ingredient) }
        return if (recipe.servings > 0) total.times(1.0 / recipe.servings) else total
    }
}
