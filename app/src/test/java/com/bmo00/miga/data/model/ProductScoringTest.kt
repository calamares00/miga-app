package com.bmo00.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductScoringTest {

    private fun info(
        grade: String? = null,
        points: Int? = null,
        additives: List<String>? = emptyList(),
        labels: List<String> = emptyList()
    ) = ProductInfo(barcode = "8410000000000", nutriScore = grade, nutriScoreValue = points, additives = additives, labels = labels)

    @Test
    fun `best nutrition without additives and not organic tops out at 90`() {
        val score = ProductScoring.compute(info(points = -15))!!
        assertEquals(90, score.value)
        assertEquals(ScoreTier.EXCELLENT, score.tier)
    }

    @Test
    fun `organic adds ten points`() {
        assertEquals(100, ProductScoring.compute(info(points = -15, labels = listOf("organic")))!!.value)
    }

    @Test
    fun `nutri-score points map consistently with the letters`() {
        val a = ProductScoring.nutritionSubscore(info(points = -2))!!
        val b = ProductScoring.nutritionSubscore(info(points = 1))!!
        val c = ProductScoring.nutritionSubscore(info(points = 6))!!
        val d = ProductScoring.nutritionSubscore(info(points = 15))!!
        val e = ProductScoring.nutritionSubscore(info(points = 25))!!
        assertTrue(a > b && b > c && c > d && d > e)
        assertEquals(0, ProductScoring.nutritionSubscore(info(points = 40)))
        assertEquals(0, ProductScoring.nutritionSubscore(info(points = 99)))
        assertEquals(100, ProductScoring.nutritionSubscore(info(points = -40)))
    }

    @Test
    fun `without points the nutri-score letter is used`() {
        assertEquals(90, ProductScoring.nutritionSubscore(info(grade = "a")))
        assertEquals(8, ProductScoring.nutritionSubscore(info(grade = "E")))
    }

    @Test
    fun `no nutri-score data means no score`() {
        assertNull(ProductScoring.compute(info()))
        assertNull(ProductScoring.compute(info(grade = "not-applicable")))
    }

    @Test
    fun `moderate and limited additives subtract from the additives subscore`() {
        // e407 moderado (-25) + e471 limitado (-10)
        assertEquals(65, ProductScoring.additivesSubscore(listOf("e407", "e471")).first)
        assertEquals(100, ProductScoring.additivesSubscore(listOf("e330")).first)
        assertEquals(100, ProductScoring.additivesSubscore(null).first)
    }

    @Test
    fun `a high risk additive zeroes the subscore and caps the total at 49`() {
        val score = ProductScoring.compute(info(points = -15, additives = listOf("e250"), labels = listOf("organic")))!!
        assertTrue(score.cappedByRiskyAdditive)
        assertEquals(0, score.additives)
        assertEquals(49, score.value)
        assertEquals(ScoreTier.MEDIOCRE, score.tier)
    }

    @Test
    fun `additives never push the subscore below zero`() {
        val many = List(8) { "e407" }
        assertEquals(0, ProductScoring.additivesSubscore(many).first)
        assertFalse(ProductScoring.additivesSubscore(many).second)
    }

    @Test
    fun `tiers follow the 75 50 25 cut points`() {
        assertEquals(ScoreTier.EXCELLENT, ProductScoring.tierFor(75))
        assertEquals(ScoreTier.GOOD, ProductScoring.tierFor(74))
        assertEquals(ScoreTier.GOOD, ProductScoring.tierFor(50))
        assertEquals(ScoreTier.MEDIOCRE, ProductScoring.tierFor(49))
        assertEquals(ScoreTier.MEDIOCRE, ProductScoring.tierFor(25))
        assertEquals(ScoreTier.BAD, ProductScoring.tierFor(24))
    }

    @Test
    fun `additive risk lookup ignores case and unknown codes are harmless`() {
        assertEquals(AdditiveRisk.HIGH, ProductScoring.additiveRisk("E171"))
        assertEquals(AdditiveRisk.MODERATE, ProductScoring.additiveRisk("e951"))
        assertEquals(AdditiveRisk.NONE, ProductScoring.additiveRisk("e330"))
    }
}
