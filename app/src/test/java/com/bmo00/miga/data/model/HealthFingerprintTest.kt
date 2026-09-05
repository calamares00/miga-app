package com.bmo00.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthFingerprintTest {

    @Test
    fun `same ingredients and steps produce the same fingerprint`() {
        val ingredients = listOf(IngredientGroup(null, listOf(Ingredient("Harina", 200.0, "g"))))
        val steps = listOf(StepGroup(null, listOf("Mezclar todo")))
        val a = HealthFingerprint.compute(ingredients, steps)
        val b = HealthFingerprint.compute(ingredients, steps)
        assertEquals(a, b)
    }

    @Test
    fun `changing an ingredient quantity changes the fingerprint`() {
        val steps = listOf(StepGroup(null, listOf("Mezclar todo")))
        val a = HealthFingerprint.compute(listOf(IngredientGroup(null, listOf(Ingredient("Harina", 200.0, "g")))), steps)
        val b = HealthFingerprint.compute(listOf(IngredientGroup(null, listOf(Ingredient("Harina", 250.0, "g")))), steps)
        assertNotEquals(a, b)
    }

    @Test
    fun `changing a step changes the fingerprint`() {
        val ingredients = listOf(IngredientGroup(null, listOf(Ingredient("Harina", 200.0, "g"))))
        val a = HealthFingerprint.compute(ingredients, listOf(StepGroup(null, listOf("Mezclar todo"))))
        val b = HealthFingerprint.compute(ingredients, listOf(StepGroup(null, listOf("Hornear 30 minutos"))))
        assertNotEquals(a, b)
    }

    @Test
    fun `fingerprint is a 64-character hex sha-256 digest`() {
        val fingerprint = HealthFingerprint.compute(emptyList(), emptyList())
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in "0123456789abcdef" })
    }
}
