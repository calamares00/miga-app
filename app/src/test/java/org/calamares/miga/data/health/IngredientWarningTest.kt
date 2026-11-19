package org.calamares.miga.data.health

import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.allergens.AllergenLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientWarningTest {

    @Test
    fun `with all allergens shown only what depends on the product is flagged`() {
        val stock = ingredientWarning("pastilla de caldo", null)!!
        assertEquals(AllergenLevel.MAY_CONTAIN, stock.level)
        assertEquals(listOf(Allergen.GLUTEN, Allergen.CELERY), stock.allergens)
        assertNull(ingredientWarning("pan rallado", null))
    }

    @Test
    fun `with only mine, only mine are flagged, and what they surely have too`() {
        val gluten = setOf(Allergen.GLUTEN)
        assertEquals(listOf(Allergen.GLUTEN), ingredientWarning("caldo", gluten)!!.allergens)
        assertNull(ingredientWarning("vinagre", gluten))
        assertEquals(AllergenLevel.CONTAINS, ingredientWarning("pan rallado", gluten)!!.level)
    }

    @Test
    fun `with allergens off nothing is flagged, unless cooking for coeliacs`() {
        val off = HealthPrefs(allergenDisplay = AllergenDisplay.NONE)
        assertNull(ingredientWarning("caldo", off.ingredientWarnings))
        val coeliac = off.copy(coeliac = true)
        assertEquals(setOf(Allergen.GLUTEN), coeliac.ingredientWarnings)
        assertNull(HealthPrefs().ingredientWarnings)
        assertEquals(
            setOf(Allergen.EGGS, Allergen.GLUTEN),
            HealthPrefs(allergenDisplay = AllergenDisplay.MINE, myAllergens = setOf(Allergen.EGGS), coeliac = true).ingredientWarnings
        )
    }
}
