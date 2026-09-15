package com.bmo00.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookModeVoiceCommandsTest {

    @Test
    fun `unrecognized phrase returns null`() {
        assertNull(CookModeVoiceCommands.parse("me gusta mucho esta receta"))
    }

    @Test
    fun `next step variants`() {
        assertEquals(CookVoiceCommand.NextStep, CookModeVoiceCommands.parse("siguiente"))
        assertEquals(CookVoiceCommand.NextStep, CookModeVoiceCommands.parse("Adelante por favor"))
        assertEquals(CookVoiceCommand.NextStep, CookModeVoiceCommands.parse("continúa"))
    }

    @Test
    fun `previous step variants`() {
        assertEquals(CookVoiceCommand.PreviousStep, CookModeVoiceCommands.parse("anterior"))
        assertEquals(CookVoiceCommand.PreviousStep, CookModeVoiceCommands.parse("vuelve atrás"))
    }

    @Test
    fun `repeat step variants`() {
        assertEquals(CookVoiceCommand.RepeatStep, CookModeVoiceCommands.parse("repite"))
        assertEquals(CookVoiceCommand.RepeatStep, CookModeVoiceCommands.parse("dilo otra vez"))
    }

    @Test
    fun `start timer when timer mentioned without a cancel verb`() {
        assertEquals(CookVoiceCommand.StartTimer, CookModeVoiceCommands.parse("temporizador"))
        assertEquals(CookVoiceCommand.StartTimer, CookModeVoiceCommands.parse("pon el temporizador"))
        assertEquals(CookVoiceCommand.StartTimer, CookModeVoiceCommands.parse("inicia la cuenta atrás"))
    }

    @Test
    fun `cancel timer when timer mentioned with a cancel verb`() {
        assertEquals(CookVoiceCommand.CancelTimer, CookModeVoiceCommands.parse("cancela el temporizador"))
        assertEquals(CookVoiceCommand.CancelTimer, CookModeVoiceCommands.parse("para el temporizador"))
        assertEquals(CookVoiceCommand.CancelTimer, CookModeVoiceCommands.parse("detén el temporizador"))
        assertEquals(CookVoiceCommand.CancelTimer, CookModeVoiceCommands.parse("quita el temporizador"))
    }

    @Test
    fun `accents are ignored`() {
        assertEquals(CookVoiceCommand.PreviousStep, CookModeVoiceCommands.parse("ATRÁS"))
    }
}
