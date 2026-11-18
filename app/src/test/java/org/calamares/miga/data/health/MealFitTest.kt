package org.calamares.miga.data.health

import org.calamares.miga.data.carbs.FoodGroup
import org.calamares.miga.data.carbs.FoodGroups
import org.calamares.miga.data.carbs.GroupPortions
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealFitTest {

    private fun recipe(servings: Int, vararg ingredients: Ingredient) = Recipe(
        id = 1, uid = "u", recipeBookId = 1, recipeBookName = "Libro", name = "Receta", categoryId = null,
        categoryName = null, difficulty = Difficulty.EASY, prepTimeMinutes = null, cookTimeMinutes = null,
        servings = servings, notes = "", source = "", isFavorite = false, timesCooked = 0, createdAt = 0, updatedAt = 0,
        photos = emptyList(), ingredientGroups = listOf(IngredientGroup(null, ingredients.toList())),
        stepGroups = emptyList(), tags = emptyList(), utensils = emptyList()
    )

    @Test
    fun `ingredients count in the method's groups`() {
        val portions = FoodGroups.ofRecipe(
            recipe(
                1,
                Ingredient("pan", 80.0, "g"),
                Ingredient("pechuga de pollo", 100.0, "g"),
                Ingredient("aceite de oliva", 1.0, "cucharada"),
                Ingredient("manzana", 1.0, null),
                Ingredient("azúcar", 10.0, "g")
            )
        )
        // The method's tables: 80 g of bread are 4 portions, 100 g of meat 2, a spoon of oil 1.
        assertEquals(4.0, portions[FoodGroup.STARCHES], 0.5)
        assertEquals(2.0, portions[FoodGroup.PROTEIN], 0.01)
        assertEquals(1.0, portions[FoodGroup.FAT], 0.1)
        assertEquals(2.0, portions[FoodGroup.FRUIT], 0.5)
        // Sugar is outside the groups.
        assertEquals(10.0, portions.otherCarbs, 0.01)
    }

    @Test
    fun `the serving fits the meal and the rest is listed`() {
        // Lentils for 4: about 4 starch portions per serving.
        val lentils = FoodGroups.ofRecipe(
            recipe(
                4,
                Ingredient("lentejas", 300.0, "g"),
                Ingredient("patatas", 2.0, null),
                Ingredient("zanahoria", 1.0, null),
                Ingredient("chorizo", 100.0, "g"),
                Ingredient("aceite de oliva", 2.0, "cucharada")
            )
        )
        val fit = MealFitter.fit(lentils, PortionPlan.EXAMPLE_1500, Meal.LUNCH)
        assertTrue("servings ${fit.servings}", fit.servings in 0.5..1.0)
        assertTrue(fit.fromRecipe[FoodGroup.STARCHES] <= 4.0)
        assertEquals(2.0, fit.missing[FoodGroup.FRUIT])
        assertTrue(FoodGroup.VEGETABLES in fit.missing)
    }

    @Test
    fun `servings go in quarters and never below a quarter`() {
        val lots = GroupPortions(mapOf(FoodGroup.STARCHES to 40.0))
        assertEquals(0.25, MealFitter.fit(lots, PortionPlan.EXAMPLE_1500, Meal.LUNCH).servings, 0.0)
        val some = GroupPortions(mapOf(FoodGroup.STARCHES to 3.0))
        assertEquals(1.25, MealFitter.fit(some, PortionPlan.EXAMPLE_1500, Meal.LUNCH).servings, 0.0)
    }

    @Test
    fun `the plan is stored and read back`() {
        val plan = PortionPlan.EXAMPLE_1500.with(Meal.LUNCH, FoodGroup.FRUIT, 0.0)
        assertEquals(plan, PortionPlan.decode(plan.encode()))
        assertEquals(0.0, plan.portions(Meal.LUNCH, FoodGroup.FRUIT), 0.0)
        assertTrue(PortionPlan.decode("{broken").isEmpty)
        assertEquals(listOf(Meal.BREAKFAST, Meal.MID_MORNING, Meal.LUNCH, Meal.AFTERNOON, Meal.DINNER, Meal.BEDTIME), PortionPlan.EXAMPLE_1500.usedMeals)
    }

    @Test
    fun `cod pil pil is one serving, with its fish counted and the confit oil capped`() {
        val pilPil = recipe(
            4,
            Ingredient("lomos de bacalao desalado con piel", 4.0, null),
            Ingredient("ajo", 6.0, "diente"),
            Ingredient("guindilla", 1.0, null),
            Ingredient("aceite de oliva virgen extra", 300.0, "ml")
        )
        val perServing = FoodGroups.ofRecipe(pilPil)
        // A 150 g loin is 2 protein portions; the oil counts 3 tablespoons per serving at most.
        assertEquals(2.0, perServing[FoodGroup.PROTEIN], 0.01)
        assertEquals(FoodGroups.MAX_OIL_PER_SERVING, perServing[FoodGroup.FAT], 0.01)
        assertTrue(perServing.cookingOil)
        val fit = MealFitter.fit(perServing, PortionPlan.EXAMPLE_1500, Meal.LUNCH)
        assertEquals(1.0, fit.servings, 0.0)
        assertEquals(4.0, fit.missing[FoodGroup.STARCHES])
        assertEquals(2.0, fit.missing[FoodGroup.FRUIT])
        assertTrue(FoodGroup.PROTEIN !in fit.missing && FoodGroup.PROTEIN !in fit.over)
    }

    @Test
    fun `a little garlic does not decide the serving`() {
        val garlicOnly = GroupPortions(mapOf(FoodGroup.VEGETABLES to 0.25, FoodGroup.FAT to 1.0))
        assertEquals(1.0, MealFitter.fit(garlicOnly, PortionPlan.EXAMPLE_1500, Meal.LUNCH).servings, 0.0)
    }

    @Test
    fun `shells, bones and stock do not count as food eaten`() {
        val paella = FoodGroups.ofRecipe(
            recipe(
                4,
                Ingredient("arroz bomba", 400.0, "g"),
                Ingredient("mejillones", 500.0, "g"),
                Ingredient("sepia o calamar limpio", 300.0, "g"),
                Ingredient("fumet de pescado", 1.2, "l"),
                Ingredient("hueso de jamón", 1.0, null)
            )
        )
        // 300 g of squid and 150 g of mussel meat: 6 portions, 1.5 each.
        assertEquals(1.5, paella[FoodGroup.PROTEIN], 0.05)
        assertEquals(0.5, MealFitter.fit(paella, PortionPlan.EXAMPLE_1500, Meal.LUNCH).servings, 0.0)
        assertEquals(0.65, FoodGroups.edibleShare("chuletas de cordero"), 0.0)
        assertEquals(1.0, FoodGroups.edibleShare("contramuslos de pollo deshuesados"), 0.0)
    }

    @Test
    fun `a sweet is one serving at most`() {
        val cake = FoodGroups.ofRecipe(
            recipe(8, Ingredient("harina", 100.0, "g"), Ingredient("azúcar", 200.0, "g"), Ingredient("mantequilla", 100.0, "g"))
        )
        assertTrue(MealFitter.fit(cake, PortionPlan.EXAMPLE_1500, Meal.LUNCH).servings <= 1.0)
    }

    @Test
    fun `fresh gnocchi are not dry pasta`() {
        val gnocchi = FoodGroups.ofRecipe(recipe(2, Ingredient("ñoquis de patata", 500.0, "g")))
        assertEquals(8.0, gnocchi[FoodGroup.STARCHES], 0.1)
    }
}
