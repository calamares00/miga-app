package org.calamares.miga.data.carbs

import org.calamares.miga.data.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbAnalysisTest {

    private fun carbs(name: String, quantity: Double? = null, unit: String? = null, optional: Boolean = false) =
        CarbAnalysis.ofIngredient(Ingredient(name, quantity, unit, optional = optional))

    private fun food(name: String) = CarbAnalysis.foodOf(name)?.id

    @Test
    fun `the longest name wins`() {
        assertEquals("chickpeas_cooked", food("garbanzos cocidos"))
        assertEquals("chickpeas", food("garbanzos"))
        assertEquals("coconut_milk", food("leche de coco"))
        assertEquals("milk", food("leche entera"))
        assertEquals("breadcrumbs", food("pan rallado"))
        assertEquals("citrus_juice", food("zumo de limón"))
        assertEquals("lemon", food("limón"))
        assertEquals("chickpea_flour", food("harina de garbanzo"))
        assertEquals("wheat_flour", food("harina de trigo de fuerza"))
        assertEquals("rice", food("arroz bomba"))
        assertEquals("stock", food("caldo de pollo"))
        assertEquals("meat", food("pechugas de pollo"))
    }

    @Test
    fun `plurals, accents and English names match`() {
        assertEquals("potato", food("Patatas nuevas"))
        assertEquals("sugar", food("AZUCAR moreno"))
        assertEquals("pasta", food("spaghetti"))
        assertEquals("wheat_flour", food("plain flour"))
        assertEquals("oil", food("aceite de oliva virgen extra"))
    }

    @Test
    fun `salt is not salsa and bread is not bacon`() {
        assertEquals("soy_sauce", food("salsa de soja"))
        assertEquals("meat", food("panceta"))
        assertEquals(null, food("xyzzy"))
    }

    @Test
    fun `grams, spoons and pieces`() {
        val rice = carbs("arroz bomba", 400.0, "g")
        assertEquals(CarbStatus.COUNTED, rice.status)
        assertEquals(400 * CarbAnalysis.foodOf("arroz")!!.carbsPer100 / 100, rice.grams, 0.01)

        // A tablespoon of flour weighs about 8 g, not 15.
        val flour = carbs("harina", 2.0, "cucharadas")
        assertEquals(2 * 15 * 0.55 * CarbAnalysis.foodOf("harina")!!.carbsPer100 / 100, flour.grams, 0.01)

        // No unit: pieces; "grande" makes them bigger.
        val onion = CarbAnalysis.foodOf("cebolla")!!
        assertEquals(onion.gramsPerPiece!! * onion.carbsPer100 / 100, carbs("cebolla", 1.0).grams, 0.01)
        assertEquals(onion.gramsPerPiece!! * 1.3 * onion.carbsPer100 / 100, carbs("cebolla grande", 1.0).grams, 0.01)

        assertEquals(1000 * CarbAnalysis.foodOf("leche")!!.carbsPer100 / 100 * 1.03, carbs("leche", 1.0, "l").grams, 0.01)
    }

    @Test
    fun `without an amount only foods with hardly any carbohydrate are free`() {
        assertEquals(CarbStatus.FREE, carbs("sal").status)
        assertEquals(CarbStatus.FREE, carbs("aceite de girasol para freír").status)
        assertEquals(CarbStatus.FREE, carbs("pollo troceado").status)
        assertEquals(CarbStatus.NO_AMOUNT, carbs("harina para rebozar").status)
        assertEquals(CarbStatus.UNKNOWN, carbs("xyzzy", 100.0, "g").status)
        // A pinch of sugar is next to nothing.
        assertEquals(CarbStatus.FREE, carbs("azúcar", 1.0, "pizca").status)
    }

    @Test
    fun `a recipe adds up its ingredients, leaves out optional ones and divides by servings`() {
        val items = listOf(
            carbs("arroz", 300.0, "g"),
            carbs("pollo", 500.0, "g"),
            carbs("pan", 100.0, "g", optional = true),
            carbs("harina para rebozar")
        )
        val recipe = RecipeCarbs(items, servings = 4)
        assertEquals(items[0].grams, recipe.total, 0.01)
        assertEquals(items[0].grams / 4, recipe.perServing!!, 0.01)
        assertEquals(listOf("harina para rebozar"), recipe.missing.map { it.name })
        assertTrue(!recipe.complete)
    }

    @Test
    fun `check values against the Spanish portion tables`() {
        // Grams of food in one 10 g portion, from the tables used in Spanish diabetes education;
        // ours must stay within 25 %. (Onion is left out: those tables say 10 g per 100 g, USDA 8
        // and BEDCA 5; we take 7.)
        val portions = mapOf(
            "arroz" to 15.0, "harina de trigo" to 13.0, "pasta" to 15.0, "pan" to 20.0, "garbanzos" to 20.0,
            "lentejas" to 20.0, "patata" to 50.0, "plátano" to 50.0, "manzana" to 75.0, "naranja" to 100.0,
            "leche" to 200.0, "azúcar" to 10.0, "miel" to 12.0, "uvas" to 50.0
        )
        portions.forEach { (name, grams) ->
            val expected = 1000.0 / grams
            val ours = CarbAnalysis.foodOf(name)!!.carbsPer100
            assertTrue("$name: $ours vs $expected", ours in expected * 0.75..expected * 1.25)
        }
    }
}
