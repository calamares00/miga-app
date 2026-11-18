package org.calamares.miga.data.health

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.carbs.FoodGroup
import org.calamares.miga.data.carbs.GroupPortions

/** The meals of a portion plan, in the order of the day. Constant names are stored. */
enum class Meal(private val labelRes: Int) {
    BREAKFAST(R.string.meal_breakfast),
    MID_MORNING(R.string.meal_mid_morning),
    LUNCH(R.string.meal_lunch),
    AFTERNOON(R.string.meal_afternoon),
    DINNER(R.string.meal_dinner),
    BEDTIME(R.string.meal_bedtime);

    val label: String get() = L10n.str(labelRes)
}

/**
 * A portion plan from the user's care team: how many portions of each [FoodGroup] each meal has.
 * Miga does not work out plans; it only helps to follow one.
 */
@Serializable
data class PortionPlan(
    /** Meal name -> group name -> portions. */
    val meals: Map<String, Map<String, Double>> = emptyMap()
) {
    fun portions(meal: Meal, group: FoodGroup): Double = meals[meal.name]?.get(group.name) ?: 0.0

    fun meal(meal: Meal): GroupPortions =
        GroupPortions(FoodGroup.entries.associateWith { portions(meal, it) }.filterValues { it > 0.0 })

    fun with(meal: Meal, group: FoodGroup, value: Double): PortionPlan {
        val row = (meals[meal.name].orEmpty() + (group.name to value.coerceIn(0.0, MAX_PORTIONS))).filterValues { it > 0.0 }
        return PortionPlan((meals + (meal.name to row)).filterValues { it.isNotEmpty() })
    }

    /** Meals with something in them, in the order of the day. */
    val usedMeals: List<Meal> get() = Meal.entries.filter { !meal(it).isEmpty }

    val isEmpty: Boolean get() = usedMeals.isEmpty()

    val kcal: Double get() = Meal.entries.sumOf { meal(it).kcal }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MAX_PORTIONS = 20.0
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String?): PortionPlan =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: PortionPlan()

        /**
         * The 1,500 kcal example of the Clínic method article (figure 1), to start from and change.
         * Mid-morning is read as 2 portions of starches and the afternoon and bedtime halves as milk.
         */
        val EXAMPLE_1500: PortionPlan = PortionPlan()
            .with(Meal.BREAKFAST, FoodGroup.MILK, 1.0).with(Meal.BREAKFAST, FoodGroup.STARCHES, 2.0).with(Meal.BREAKFAST, FoodGroup.FAT, 1.0)
            .with(Meal.MID_MORNING, FoodGroup.STARCHES, 2.0)
            .with(Meal.LUNCH, FoodGroup.PROTEIN, 2.0).with(Meal.LUNCH, FoodGroup.VEGETABLES, 1.0).with(Meal.LUNCH, FoodGroup.STARCHES, 4.0)
            .with(Meal.LUNCH, FoodGroup.FRUIT, 2.0).with(Meal.LUNCH, FoodGroup.FAT, 1.0)
            .with(Meal.AFTERNOON, FoodGroup.MILK, 0.5)
            .with(Meal.DINNER, FoodGroup.PROTEIN, 2.0).with(Meal.DINNER, FoodGroup.VEGETABLES, 1.0).with(Meal.DINNER, FoodGroup.STARCHES, 4.0)
            .with(Meal.DINNER, FoodGroup.FRUIT, 2.0).with(Meal.DINNER, FoodGroup.FAT, 1.0)
            .with(Meal.BEDTIME, FoodGroup.MILK, 0.5)
    }
}
