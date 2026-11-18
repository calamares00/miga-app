package org.calamares.miga.data.health

import org.calamares.miga.data.carbs.FoodGroup
import org.calamares.miga.data.carbs.GroupPortions

/**
 * How a recipe fits a meal of the plan: how many servings of it to take, what they bring and what
 * is left to add (or over) in each group.
 */
data class MealFit(
    val meal: Meal,
    /** Servings of the recipe, in quarters. */
    val servings: Double,
    val fromRecipe: GroupPortions,
    /** Portions still to add, per group (half portions and up). */
    val missing: Map<FoodGroup, Double>,
    /** Portions over the plan, per group (half portions and up). */
    val over: Map<FoodGroup, Double>,
    /**
     * False when the recipe's main group is not in this meal of the plan (a burger at bedtime,
     * when the plan only has milk): then there is no sensible serving.
     */
    val suitable: Boolean = true,
    /** The group the recipe has most of, fat aside. */
    val mainGroup: FoodGroup? = null
)

object MealFitter {
    /** The groups that set the serving, in order: the first one the recipe and the meal share. */
    private val ANCHORS = listOf(FoodGroup.STARCHES, FoodGroup.PROTEIN, FoodGroup.MILK, FoodGroup.FRUIT, FoodGroup.VEGETABLES)
    private const val MIN_SERVINGS = 0.25
    /** More than two servings of one dish is never a sensible answer, whatever the plan says. */
    private const val MAX_SERVINGS = 2.0
    /** A group sets the serving only when one serving has at least this much of it (not the garlic). */
    private const val ANCHOR_MIN = 0.5
    /** Grams of carbohydrate outside the groups (sugar) from which a serving is a sweet. */
    private const val SWEET_CARBS = 10.0

    /**
     * The serving (in quarters) that keeps the recipe within the meal in every group both
     * have, fat aside: oil is easy to change and the method treats it loosely. Groups the recipe
     * only has a little of do not count, and with none left the answer is one serving.
     */
    fun fit(perServing: GroupPortions, plan: PortionPlan, meal: Meal): MealFit {
        val target = plan.meal(meal)
        val main = mainGroup(perServing)
        if (main != null && target[main] <= 0.0) {
            return MealFit(meal, 0.0, GroupPortions(), emptyMap(), emptyMap(), suitable = false, mainGroup = main)
        }
        val ratios = ANCHORS.filter { perServing[it] >= ANCHOR_MIN && target[it] > 0.0 }.map { target[it] / perServing[it] }
        val byGroups = ratios.minOrNull() ?: 1.0
        // Never more energy than the whole meal, and sweets (sugar outside the groups) at most one serving.
        val byKcal = if (perServing.kcal > 0 && target.kcal > 0) target.kcal / perServing.kcal else Double.MAX_VALUE
        val bySugar = if (perServing.otherCarbs >= SWEET_CARBS) 1.0 else Double.MAX_VALUE
        val raw = minOf(byGroups, byKcal, bySugar)
        // The nearest quarter, unless that goes more than 15 % over: 0.48 is half a serving, not a quarter.
        val nearest = Math.round(raw * 4) / 4.0
        val servings = (if (nearest > raw * 1.15) nearest - 0.25 else nearest).coerceIn(MIN_SERVINGS, MAX_SERVINGS)
        val taken = perServing.times(servings)
        val missing = FoodGroup.entries.associateWith { halves(target[it] - taken[it]) }.filterValues { it > 0.0 }
        val over = FoodGroup.entries.associateWith { halves(taken[it] - target[it]) }.filterValues { it > 0.0 }
        return MealFit(meal, servings, taken, missing, over, mainGroup = main)
    }

    /** The meals of the plan this recipe suits, in the order of the day. */
    fun suitableMeals(perServing: GroupPortions, plan: PortionPlan): List<Meal> =
        plan.usedMeals.filter { fit(perServing, plan, it).suitable }

    /** The group a serving has most of (at least half a portion), fat aside; null for none. */
    fun mainGroup(perServing: GroupPortions): FoodGroup? =
        FoodGroup.entries.filter { it != FoodGroup.FAT && perServing[it] >= ANCHOR_MIN }.maxByOrNull { perServing[it] }

    /** To the nearest half portion; less than a quarter is nothing. */
    private fun halves(value: Double): Double = if (value < 0.25) 0.0 else Math.round(value * 2) / 2.0
}
