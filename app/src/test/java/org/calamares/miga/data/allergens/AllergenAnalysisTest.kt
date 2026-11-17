package org.calamares.miga.data.allergens

import org.calamares.miga.data.allergens.Allergen.CELERY
import org.calamares.miga.data.allergens.Allergen.EGGS
import org.calamares.miga.data.allergens.Allergen.GLUTEN
import org.calamares.miga.data.allergens.Allergen.LACTOSE
import org.calamares.miga.data.allergens.Allergen.MILK
import org.calamares.miga.data.allergens.Allergen.SOY
import org.calamares.miga.data.allergens.Allergen.SULPHITES
import org.calamares.miga.data.allergens.Allergen.TREE_NUTS
import org.calamares.miga.data.allergens.AllergenLevel.CONTAINS
import org.calamares.miga.data.allergens.AllergenLevel.FREE
import org.calamares.miga.data.allergens.AllergenLevel.MAY_CONTAIN
import org.calamares.miga.data.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllergenAnalysisTest {

    private fun levels(name: String) = AllergenAnalysis.ofIngredient(name).levels
    private fun recipe(vararg names: String) = AllergenAnalysis.ofIngredients(names.map { Ingredient(it, null, null) })

    @Test
    fun `common ingredients, with accents and plurals`() {
        assertEquals(mapOf(GLUTEN to CONTAINS), levels("Harina de trigo"))
        assertEquals(mapOf(EGGS to CONTAINS), levels("huevos camperos"))
        assertEquals(mapOf(TREE_NUTS to CONTAINS), levels("Nueces peladas"))
        assertEquals(mapOf(MILK to CONTAINS, LACTOSE to CONTAINS), levels("Leche entera"))
        assertEquals(mapOf(MILK to CONTAINS, LACTOSE to MAY_CONTAIN), levels("queso parmesano rallado"))
        assertEquals(mapOf(SULPHITES to CONTAINS), levels("1 vaso de vino blanco"))
        assertEquals(CONTAINS, levels("Espelta")[GLUTEN])
    }

    @Test
    fun `names that look like an allergen but are not`() {
        assertTrue(levels("nuez moscada").isEmpty())
        assertTrue(levels("leche de coco").isEmpty())
        assertTrue(levels("panceta").isEmpty())
        assertTrue(levels("brotes de soja").isEmpty())
        assertTrue(levels("pasta de tomate").isEmpty())
        assertTrue(levels("cebolla en corte fino").isEmpty())
        assertEquals(mapOf(TREE_NUTS to CONTAINS), levels("leche de almendras"))
        // Buckwheat is not wheat: no gluten, only possible traces (AESAN).
        assertTrue(levels("harina de trigo sarraceno").isEmpty())
        assertEquals(setOf(GLUTEN), AllergenAnalysis.ofIngredient("harina de trigo sarraceno").traces)
        assertTrue(AllergenAnalysis.ofIngredient("harina de trigo sarraceno sin gluten").traces.isEmpty())
    }

    @Test
    fun `what depends on the product, and certified products`() {
        assertEquals(mapOf(GLUTEN to MAY_CONTAIN, CELERY to MAY_CONTAIN), levels("1 pastilla de caldo de pollo"))
        assertEquals(MAY_CONTAIN, levels("chorizo")[GLUTEN])
        assertTrue(levels("lentejas pardinas").isEmpty())
        assertEquals(setOf(GLUTEN), AllergenAnalysis.ofIngredient("lentejas pardinas").traces)
        assertEquals(CONTAINS, levels("copos de avena")[GLUTEN])
        assertTrue(levels("avena sin gluten").isEmpty())
        assertTrue(levels("chorizo sin gluten ni lactosa").let { GLUTEN !in it })
        assertTrue(levels("pasta sin gluten").isEmpty())
        assertEquals(mapOf(MILK to CONTAINS), levels("leche sin lactosa"))
        assertEquals(mapOf(GLUTEN to CONTAINS, SOY to CONTAINS), levels("salsa de soja"))
    }

    @Test
    fun `a recipe takes the worst level and remembers the causes`() {
        val lentils = recipe("lentejas", "chorizo", "1 pastilla de caldo", "apio", "vino blanco", "zanahoria")
        assertEquals(MAY_CONTAIN, lentils.level(GLUTEN))
        assertEquals(CONTAINS, lentils.level(CELERY))
        assertEquals(CONTAINS, lentils.level(SULPHITES))
        assertEquals(FREE, lentils.level(EGGS))
        assertEquals(listOf("chorizo", "1 pastilla de caldo"), lentils.causes.getValue(GLUTEN).map { it.ingredient })
        assertEquals(listOf("lentejas"), lentils.traceCauses[GLUTEN])

        // A gluten-free recipe with buckwheat flour: no gluten, the flour only as traces.
        val crepes = recipe("harina de trigo sarraceno", "huevo para la masa", "mantequilla", "jamón cocido sin gluten", "queso emmental rallado")
        assertEquals(FREE, crepes.level(GLUTEN))
        assertEquals(listOf("harina de trigo sarraceno"), crepes.traceCauses[GLUTEN])

        // Polenta (maize semolina): no gluten either; wheat semolina has.
        val polenta = recipe("polenta (sémola de maíz)", "caldo de verduras sin gluten", "queso parmesano rallado", "setas")
        assertEquals(FREE, polenta.level(GLUTEN))
        assertEquals(listOf("polenta (sémola de maíz)"), polenta.traceCauses[GLUTEN])
        assertEquals(MAY_CONTAIN, polenta.level(CELERY))
        assertEquals(CONTAINS, levels("sémola de trigo")[GLUTEN])
        assertEquals(CONTAINS, levels("sémola")[GLUTEN])
        // CONTAINS first, then MAY_CONTAIN, each in the EU order.
        assertEquals(listOf(CELERY, SULPHITES, GLUTEN, MILK, LACTOSE), lentils.present)

        val tortilla = recipe("huevos", "patatas", "cebolla", "aceite de oliva", "sal")
        assertEquals(listOf(EGGS), tortilla.present)
    }

}
