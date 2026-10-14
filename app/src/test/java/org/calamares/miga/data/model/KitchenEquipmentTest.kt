package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenEquipmentTest {

    @Test
    fun `usual variants become the default name in the app language`() {
        assertEquals("Freidora de aire", KitchenEquipment.canonical("Airfryer", "es"))
        assertEquals("Air fryer", KitchenEquipment.canonical("freidora sin aceite", "en"))
        assertEquals("Olla exprés", KitchenEquipment.canonical("Olla Express", "es"))
        assertEquals("Plancha", KitchenEquipment.canonical("Parrilla / Plancha", "es"))
        assertEquals(listOf("Freidora de aire", "Air fryer"), KitchenEquipment.equivalents("AIRFRYER"))
    }

    @Test
    fun `brands, models and unknown names are kept as written`() {
        assertEquals("Thermomix TM31", KitchenEquipment.canonical(" Thermomix  TM31 ", "es"))
        assertEquals("Mandolina", KitchenEquipment.canonical("Mandolina", "es"))
        assertTrue(KitchenEquipment.equivalents("Mandolina").isEmpty())
    }

    @Test
    fun `basics are obvious and dropped from AI results`() {
        assertTrue(KitchenEquipment.isObvious("Cuchillo"))
        assertTrue(KitchenEquipment.isObvious("Sartén"))
        assertTrue(KitchenEquipment.isObvious("frying pan"))
        assertFalse(KitchenEquipment.isObvious("Horno"))
        assertEquals(
            listOf("Horno", "Freidora de aire"),
            KitchenEquipment.clean(listOf("Cuchillo", "horno", "Airfryer", "Freidora de aire", " ", "Bol"), "es")
        )
    }

    @Test
    fun `defaults have no obvious items and are their own canonical names`() {
        for (language in listOf("es", "en")) {
            KitchenEquipment.defaults(language).forEach { name ->
                assertFalse(name, KitchenEquipment.isObvious(name))
                assertEquals(name, KitchenEquipment.canonical(name, language))
            }
        }
    }
}
