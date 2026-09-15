package com.bmo00.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepTimerParsingTest {

    @Test
    fun `no duration mentioned returns null`() {
        assertNull(StepTimerParsing.findTimerSeconds("Mezcla los ingredientes en un bol grande"))
    }

    @Test
    fun `minutes alone`() {
        assertEquals(600, StepTimerParsing.findTimerSeconds("Cuece a fuego medio durante 10 minutos"))
    }

    @Test
    fun `minutes abbreviated`() {
        assertEquals(300, StepTimerParsing.findTimerSeconds("Deja reposar 5 min"))
    }

    @Test
    fun `hours alone`() {
        assertEquals(7200, StepTimerParsing.findTimerSeconds("Hornea durante 2 horas"))
    }

    @Test
    fun `seconds alone`() {
        assertEquals(30, StepTimerParsing.findTimerSeconds("Saltea 30 segundos y retira del fuego"))
    }

    @Test
    fun `hours and minutes combine when mentioned close together`() {
        assertEquals(5400, StepTimerParsing.findTimerSeconds("Hornea durante 1 hora y 30 minutos"))
    }

    @Test
    fun `two unrelated durations use only the first one`() {
        assertEquals(
            300,
            StepTimerParsing.findTimerSeconds(
                "Deja reposar la masa 5 minutos a temperatura ambiente y después hornea 40 minutos"
            )
        )
    }

    @Test
    fun `a duration of zero is treated as no timer`() {
        assertNull(StepTimerParsing.findTimerSeconds("Repite el paso 0 veces"))
    }
}
