package org.calamares.miga.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerLanguageTest {

    @Test
    fun `a Spanish recipe is detected as Spanish despite the English keys`() {
        val json = """{"name":"Tortilla de patatas","ingredientGroups":[{"name":null,"ingredients":[{"name":"patatas","quantity":4,"unit":null},{"name":"huevos","quantity":6,"unit":null}]}],"stepGroups":[{"name":null,"instructions":["Pelar las patatas y cortarlas en láminas finas.","Freír las patatas en el aceite durante 20 minutos hasta que estén tiernas.","Batir los huevos con la sal y mezclar con las patatas."]}],"notes":"Se puede hacer con cebolla."}"""
        assertEquals("es", AnswerLanguage.detect(json))
    }

    @Test
    fun `an English recipe is detected as English`() {
        val json = """{"name":"Spanish omelette","stepGroups":[{"name":null,"instructions":["Peel the potatoes and slice them thinly.","Fry the potatoes in the oil for 20 minutes until tender.","Beat the eggs with the salt and mix with the potatoes."]}],"notes":"It can be made with onion."}"""
        assertEquals("en", AnswerLanguage.detect(json))
    }

    @Test
    fun `too little text gives no verdict`() {
        assertNull(AnswerLanguage.detect("""{"name":"Gazpacho","ingredientGroups":[]}"""))
    }
}
