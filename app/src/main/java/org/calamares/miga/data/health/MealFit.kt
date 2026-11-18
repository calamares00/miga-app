package org.calamares.miga.data.health

import org.calamares.miga.data.carbs.FoodGroup
import org.calamares.miga.data.carbs.GroupPortions
import kotlin.math.floor

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
    val over: Map<FoodGroup, Double>
)

object MealFitter {
    /** The groups that set the serving, in order: the first one the recipe and the meal share. */
    private val ANCHORS = listOf(FoodGroup.STARCHES, FoodGroup.PROTEIN, FoodGroup.MILK, FoodGroup.FRUIT, FoodGroup.VEGETABLES)
    private const val MIN_SERVINGS = 0.25
    private const val MAX_SERVINGS = 4.0

    /**
     * The largest serving (in quarters) that keeps the recipe within the meal in every group both
     * have, fat aside: oil is easy to change and the method treats it loosely.
     */
    fun fit(perServing: GroupPortions, plan: PortionPlan, meal: Meal): MealFit {
        val target = plan.meal(meal)
        val ratios = ANCHORS.filter { perServing[it] > 0.0 && target[it] > 0.0 }.map { target[it] / perServing[it] }
        val raw = ratios.minOrNull() ?: 1.0
        val servings = (floor(raw * 4) / 4).coerceIn(MIN_SERVINGS, MAX_SERVINGS)
        val taken = perServing.times(servings)
        val missing = FoodGroup.entries.associateWith { halves(target[it] - taken[it]) }.filterValues { it > 0.0 }
        val over = FoodGroup.entries.associateWith { halves(taken[it] - target[it]) }.filterValues { it > 0.0 }
        return MealFit(meal, servings, taken, missing, over)
    }

    /** To the nearest half portion; less than a quarter is nothing. */
    private fun halves(value: Double): Double = if (value < 0.25) 0.0 else Math.round(value * 2) / 2.0
}
